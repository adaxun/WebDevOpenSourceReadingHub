/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import jdk.internal.misc.Unsafe;


sealed



class ByteBufferAsDoubleBufferB                  // package-private
    extends DoubleBuffer

    permits ByteBufferAsDoubleBufferRB

{



    protected final ByteBuffer bb;



    ByteBufferAsDoubleBufferB(ByteBuffer bb, MemorySegment segment) {   // package-private

        super(-1, 0,
              bb.remaining() >> 3,
              bb.remaining() >> 3, segment);
        this.bb = bb;
        // enforce limit == capacity
        int cap = this.capacity();
        this.limit(cap);
        int pos = this.position();
        assert (pos <= cap);
        address = bb.address;



    }

    ByteBufferAsDoubleBufferB(ByteBuffer bb,
                                     int mark, int pos, int lim, int cap,
                                     long addr, MemorySegment segment)
    {

        super(mark, pos, lim, cap, segment);
        this.bb = bb;
        address = addr;
        assert address >= bb.address;



    }

    @Override
    Object base() {
        return bb.hb;
    }

    public DoubleBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        long addr = byteOffset(pos);
        return new ByteBufferAsDoubleBufferB(bb, -1, 0, rem, rem, addr, segment);
    }

    @Override
    public DoubleBuffer slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        return new ByteBufferAsDoubleBufferB(bb,
                                                    -1,
                                                    0,
                                                    length,
                                                    length,
                                                    byteOffset(index), segment);
    }

    public DoubleBuffer duplicate() {
        return new ByteBufferAsDoubleBufferB(bb,
                                                    this.markValue(),
                                                    this.position(),
                                                    this.limit(),
                                                    this.capacity(),
                                                    address, segment);
    }

    public DoubleBuffer asReadOnlyBuffer() {

        return new ByteBufferAsDoubleBufferRB(bb,
                                                 this.markValue(),
                                                 this.position(),
                                                 this.limit(),
                                                 this.capacity(),
                                                 address, segment);



    }



    private int ix(int i) {
        int off = (int) (address - bb.address);
        return (i << 3) + off;
    }

    protected long byteOffset(long i) {
        return (i << 3) + address;
    }

    public double get() {
        long x = SCOPED_MEMORY_ACCESS.getLongUnaligned(session(), bb.hb, byteOffset(nextGetIndex()),
            true);
        return Double.longBitsToDouble(x);
    }

    public double get(int i) {
        long x = SCOPED_MEMORY_ACCESS.getLongUnaligned(session(), bb.hb, byteOffset(checkIndex(i)),
            true);
        return Double.longBitsToDouble(x);
    }











    public DoubleBuffer put(double x) {

        long y = Double.doubleToRawLongBits(x);
        SCOPED_MEMORY_ACCESS.putLongUnaligned(session(), bb.hb, byteOffset(nextPutIndex()), y,
            true);
        return this;



    }

    public DoubleBuffer put(int i, double x) {

        long y = Double.doubleToRawLongBits(x);
        SCOPED_MEMORY_ACCESS.putLongUnaligned(session(), bb.hb, byteOffset(checkIndex(i)), y,
            true);
        return this;



    }

    public DoubleBuffer compact() {

        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        ByteBuffer db = bb.duplicate();
        db.limit(ix(lim));
        db.position(ix(0));
        ByteBuffer sb = db.slice();
        sb.position(pos << 3);
        sb.compact();
        position(rem);
        limit(capacity());
        discardMark();
        return this;



    }

    public boolean isDirect() {
        return bb.isDirect();
    }

    public boolean isReadOnly() {
        return false;
    }





































    public ByteOrder order() {

        return ByteOrder.BIG_ENDIAN;




    }






}
