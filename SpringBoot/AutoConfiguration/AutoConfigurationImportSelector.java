package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.context.annotation.DeferredImportSelector.Group.Entry;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class AutoConfigurationImportSelector implements DeferredImportSelector, BeanClassLoaderAware, ResourceLoaderAware, BeanFactoryAware, EnvironmentAware, Ordered {
    private static final AutoConfigurationImportSelector.AutoConfigurationEntry EMPTY_ENTRY = new AutoConfigurationImportSelector.AutoConfigurationEntry();
    private static final String[] NO_IMPORTS = new String[0];
    private static final Log logger = LogFactory.getLog(AutoConfigurationImportSelector.class);
    private static final String PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";
    private ConfigurableListableBeanFactory beanFactory;
    private Environment environment;
    private ClassLoader beanClassLoader;
    private ResourceLoader resourceLoader;
    private AutoConfigurationImportSelector.ConfigurationClassFilter configurationClassFilter;

    public AutoConfigurationImportSelector() {
    }

    public String[] selectImports(AnnotationMetadata annotationMetadata) {
        if (!this.isEnabled(annotationMetadata)) {
            return NO_IMPORTS;//返回空String[]
        } else {
            AutoConfigurationImportSelector.AutoConfigurationEntry autoConfigurationEntry = this.getAutoConfigurationEntry(annotationMetadata);
            return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());// 最终返回String数组
        }
    }

    public Predicate<String> getExclusionFilter() {
        return this::shouldExclude;
    }

    private boolean shouldExclude(String configurationClassName) {
        return this.getConfigurationClassFilter().filter(Collections.singletonList(configurationClassName)).isEmpty();
    }

    protected AutoConfigurationImportSelector.AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
        if (!this.isEnabled(annotationMetadata)) {
            return EMPTY_ENTRY;
        } else {
            AnnotationAttributes attributes = this.getAttributes(annotationMetadata);//加载metadata配置
            List<String> configurations = this.getCandidateConfigurations(annotationMetadata, attributes);//加载attributes 的jar/META—INF/spring.factories 下的EnableAutoConfiguration配置类
            configurations = this.removeDuplicates(configurations);// 获取去重后的配置类
            Set<String> exclusions = this.getExclusions(annotationMetadata, attributes);//获取不要的配置类
            this.checkExcludedClasses(configurations, exclusions);//检查合法性 不合法的加入不要的配置类里
            configurations.removeAll(exclusions);//排除不要的配置类
            configurations = this.getConfigurationClassFilter().filter(configurations);
            this.fireAutoConfigurationImportEvents(configurations, exclusions);// 通过事件传入到监听器中
            return new AutoConfigurationImportSelector.AutoConfigurationEntry(configurations, exclusions);
        }
    }

    public Class<? extends Group> getImportGroup() {
        return AutoConfigurationImportSelector.AutoConfigurationGroup.class;
    }

    protected boolean isEnabled(AnnotationMetadata metadata) {
        return this.getClass() == AutoConfigurationImportSelector.class ? (Boolean)this.getEnvironment().getProperty("spring.boot.enableautoconfiguration", Boolean.class, true) : true;
    }

    protected AnnotationAttributes getAttributes(AnnotationMetadata metadata) {
        String name = this.getAnnotationClass().getName();
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(name, true));
        Assert.notNull(attributes, () -> {
            return "No auto-configuration attributes found. Is " + metadata.getClassName() + " annotated with " + ClassUtils.getShortName(name) + "?";
        });
        return attributes;
    }

    protected Class<?> getAnnotationClass() {
        return EnableAutoConfiguration.class;
    }

    protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
        List<String> configurations = SpringFactoriesLoader.loadFactoryNames(this.getSpringFactoriesLoaderFactoryClass(), this.getBeanClassLoader());
        Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you are using a custom packaging, make sure that file is correct.");
        return configurations;
    }

    protected Class<?> getSpringFactoriesLoaderFactoryClass() {
        return EnableAutoConfiguration.class;
    }

    private void checkExcludedClasses(List<String> configurations, Set<String> exclusions) {
        List<String> invalidExcludes = new ArrayList(exclusions.size());
        Iterator var4 = exclusions.iterator();

        while(var4.hasNext()) {
            String exclusion = (String)var4.next();
            if (ClassUtils.isPresent(exclusion, this.getClass().getClassLoader()) && !configurations.contains(exclusion)) {
                invalidExcludes.add(exclusion);
            }
        }

        if (!invalidExcludes.isEmpty()) {
            this.handleInvalidExcludes(invalidExcludes);
        }

    }

    protected void handleInvalidExcludes(List<String> invalidExcludes) {
        StringBuilder message = new StringBuilder();
        Iterator var3 = invalidExcludes.iterator();

        while(var3.hasNext()) {
            String exclude = (String)var3.next();
            message.append("\t- ").append(exclude).append(String.format("%n"));
        }

        throw new IllegalStateException(String.format("The following classes could not be excluded because they are not auto-configuration classes:%n%s", message));
    }

    protected Set<String> getExclusions(AnnotationMetadata metadata, AnnotationAttributes attributes) {
        Set<String> excluded = new LinkedHashSet();
        excluded.addAll(this.asList(attributes, "exclude"));
        excluded.addAll(Arrays.asList(attributes.getStringArray("excludeName")));
        excluded.addAll(this.getExcludeAutoConfigurationsProperty());
        return excluded;
    }

    protected List<String> getExcludeAutoConfigurationsProperty() {
        Environment environment = this.getEnvironment();
        if (environment == null) {
            return Collections.emptyList();
        } else if (environment instanceof ConfigurableEnvironment) {
            Binder binder = Binder.get(environment);
            return (List)binder.bind("spring.autoconfigure.exclude", String[].class).map(Arrays::asList).orElse(Collections.emptyList());
        } else {
            String[] excludes = (String[])environment.getProperty("spring.autoconfigure.exclude", String[].class);
            return excludes != null ? Arrays.asList(excludes) : Collections.emptyList();
        }
    }

    protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
        return SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class, this.beanClassLoader);
    }

    private AutoConfigurationImportSelector.ConfigurationClassFilter getConfigurationClassFilter() {
        if (this.configurationClassFilter == null) {
            List<AutoConfigurationImportFilter> filters = this.getAutoConfigurationImportFilters();
            Iterator var2 = filters.iterator();

            while(var2.hasNext()) {
                AutoConfigurationImportFilter filter = (AutoConfigurationImportFilter)var2.next();
                this.invokeAwareMethods(filter);
            }

            this.configurationClassFilter = new AutoConfigurationImportSelector.ConfigurationClassFilter(this.beanClassLoader, filters);
        }

        return this.configurationClassFilter;
    }

    protected final <T> List<T> removeDuplicates(List<T> list) {
        return new ArrayList(new LinkedHashSet(list));
    }

    protected final List<String> asList(AnnotationAttributes attributes, String name) {
        String[] value = attributes.getStringArray(name);
        return Arrays.asList(value);
    }

    private void fireAutoConfigurationImportEvents(List<String> configurations, Set<String> exclusions) {
        List<AutoConfigurationImportListener> listeners = this.getAutoConfigurationImportListeners();
        if (!listeners.isEmpty()) {
            AutoConfigurationImportEvent event = new AutoConfigurationImportEvent(this, configurations, exclusions);
            Iterator var5 = listeners.iterator();

            while(var5.hasNext()) {
                AutoConfigurationImportListener listener = (AutoConfigurationImportListener)var5.next();
                this.invokeAwareMethods(listener);
                listener.onAutoConfigurationImportEvent(event);
            }
        }

    }

    protected List<AutoConfigurationImportListener> getAutoConfigurationImportListeners() {
        return SpringFactoriesLoader.loadFactories(AutoConfigurationImportListener.class, this.beanClassLoader);
    }

    private void invokeAwareMethods(Object instance) {
        if (instance instanceof Aware) {
            if (instance instanceof BeanClassLoaderAware) {
                ((BeanClassLoaderAware)instance).setBeanClassLoader(this.beanClassLoader);
            }

            if (instance instanceof BeanFactoryAware) {
                ((BeanFactoryAware)instance).setBeanFactory(this.beanFactory);
            }

            if (instance instanceof EnvironmentAware) {
                ((EnvironmentAware)instance).setEnvironment(this.environment);
            }

            if (instance instanceof ResourceLoaderAware) {
                ((ResourceLoaderAware)instance).setResourceLoader(this.resourceLoader);
            }
        }

    }

    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory);
        this.beanFactory = (ConfigurableListableBeanFactory)beanFactory;
    }

    protected final ConfigurableListableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    protected ClassLoader getBeanClassLoader() {
        return this.beanClassLoader;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    protected final Environment getEnvironment() {
        return this.environment;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    protected final ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    public int getOrder() {
        return 2147483646;
    }

    protected static class AutoConfigurationEntry {
        private final List<String> configurations;
        private final Set<String> exclusions;

        private AutoConfigurationEntry() {
            this.configurations = Collections.emptyList();
            this.exclusions = Collections.emptySet();
        }

        AutoConfigurationEntry(Collection<String> configurations, Collection<String> exclusions) {
            this.configurations = new ArrayList(configurations);
            this.exclusions = new HashSet(exclusions);
        }

        public List<String> getConfigurations() {
            return this.configurations;
        }

        public Set<String> getExclusions() {
            return this.exclusions;
        }
    }

    private static class AutoConfigurationGroup implements Group, BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {
        private final Map<String, AnnotationMetadata> entries = new LinkedHashMap();
        private final List<AutoConfigurationImportSelector.AutoConfigurationEntry> autoConfigurationEntries = new ArrayList();
        private ClassLoader beanClassLoader;
        private BeanFactory beanFactory;
        private ResourceLoader resourceLoader;
        private AutoConfigurationMetadata autoConfigurationMetadata;

        private AutoConfigurationGroup() {
        }

        public void setBeanClassLoader(ClassLoader classLoader) {
            this.beanClassLoader = classLoader;
        }

        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        public void setResourceLoader(ResourceLoader resourceLoader) {
            this.resourceLoader = resourceLoader;
        }

        public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
            Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector, () -> {
                return String.format("Only %s implementations are supported, got %s", AutoConfigurationImportSelector.class.getSimpleName(), deferredImportSelector.getClass().getName());
            });
            AutoConfigurationImportSelector.AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector)deferredImportSelector).getAutoConfigurationEntry(annotationMetadata);
            this.autoConfigurationEntries.add(autoConfigurationEntry);
            Iterator var4 = autoConfigurationEntry.getConfigurations().iterator();

            while(var4.hasNext()) {
                String importClassName = (String)var4.next();
                this.entries.putIfAbsent(importClassName, annotationMetadata);
            }

        }

        public Iterable<Entry> selectImports() {
            if (this.autoConfigurationEntries.isEmpty()) {
                return Collections.emptyList();
            } else {
                Set<String> allExclusions = (Set)this.autoConfigurationEntries.stream().map(AutoConfigurationImportSelector.AutoConfigurationEntry::getExclusions).flatMap(Collection::stream).collect(Collectors.toSet());
                Set<String> processedConfigurations = (Set)this.autoConfigurationEntries.stream().map(AutoConfigurationImportSelector.AutoConfigurationEntry::getConfigurations).flatMap(Collection::stream).collect(Collectors.toCollection(LinkedHashSet::new));
                processedConfigurations.removeAll(allExclusions);
                return (Iterable)this.sortAutoConfigurations(processedConfigurations, this.getAutoConfigurationMetadata()).stream().map((importClassName) -> {
                    return new Entry((AnnotationMetadata)this.entries.get(importClassName), importClassName);
                }).collect(Collectors.toList());
            }
        }

        private AutoConfigurationMetadata getAutoConfigurationMetadata() {
            if (this.autoConfigurationMetadata == null) {
                this.autoConfigurationMetadata = AutoConfigurationMetadataLoader.loadMetadata(this.beanClassLoader);
            }

            return this.autoConfigurationMetadata;
        }

        private List<String> sortAutoConfigurations(Set<String> configurations, AutoConfigurationMetadata autoConfigurationMetadata) {
            return (new AutoConfigurationSorter(this.getMetadataReaderFactory(), autoConfigurationMetadata)).getInPriorityOrder(configurations);
        }

        private MetadataReaderFactory getMetadataReaderFactory() {
            try {
                return (MetadataReaderFactory)this.beanFactory.getBean("org.springframework.boot.autoconfigure.internalCachingMetadataReaderFactory", MetadataReaderFactory.class);
            } catch (NoSuchBeanDefinitionException var2) {
                return new CachingMetadataReaderFactory(this.resourceLoader);
            }
        }
    }

    private static class ConfigurationClassFilter {
        private final AutoConfigurationMetadata autoConfigurationMetadata;
        private final List<AutoConfigurationImportFilter> filters;

        ConfigurationClassFilter(ClassLoader classLoader, List<AutoConfigurationImportFilter> filters) {
            this.autoConfigurationMetadata = AutoConfigurationMetadataLoader.loadMetadata(classLoader);
            this.filters = filters;
        }

        List<String> filter(List<String> configurations) {
            long startTime = System.nanoTime();
            String[] candidates = StringUtils.toStringArray(configurations);
            boolean skipped = false;
            Iterator var6 = this.filters.iterator();

            int i;
            while(var6.hasNext()) {
                AutoConfigurationImportFilter filter = (AutoConfigurationImportFilter)var6.next();
                boolean[] match = filter.match(candidates, this.autoConfigurationMetadata);

                for(i = 0; i < match.length; ++i) {
                    if (!match[i]) {
                        candidates[i] = null;
                        skipped = true;
                    }
                }
            }

            if (!skipped) {
                return configurations;
            } else {
                List<String> result = new ArrayList(candidates.length);
                String[] var12 = candidates;
                int var14 = candidates.length;

                for(i = 0; i < var14; ++i) {
                    String candidate = var12[i];
                    if (candidate != null) {
                        result.add(candidate);
                    }
                }

                if (AutoConfigurationImportSelector.logger.isTraceEnabled()) {
                    int numberFiltered = configurations.size() - result.size();
                    AutoConfigurationImportSelector.logger.trace("Filtered " + numberFiltered + " auto configuration class in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");
                }

                return result;
            }
        }
    }
}
