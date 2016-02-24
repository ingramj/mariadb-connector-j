/*
MariaDB Client for Java

Copyright (c) 2012 Monty Program Ab.

This library is free software; you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the Free
Software Foundation; either version 2.1 of the License, or (at your option)
any later version.

This library is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License along
with this library; if not, write to Monty Program Ab info@montyprogram.com.

This particular MariaDB Client for Java file is work
derived from a Drizzle-JDBC. Drizzle-JDBC file which is covered by subject to
the following copyright and notice provisions:

Copyright (c) 2009-2011, Marcus Eriksson

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of the driver nor the names of its contributors may not be
used to endorse or promote products derived from this software without specific
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS  AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
*/

package org.mariadb.jdbc.internal.util.dao;

import org.mariadb.jdbc.internal.packet.dao.ColumnInformation;

import java.util.concurrent.atomic.AtomicBoolean;

public class PrepareResult {
    private final int statementId;
    private ColumnInformation[] columns;
    private ColumnInformation[] parameters;

    //share indicator
    private volatile int shareCounter = 1;
    private volatile boolean isBeingDeallocate;
    private AtomicBoolean inCache = new AtomicBoolean();

    /**
     * PrepareStatement Result object.
     * @param statementId server statement Id.
     * @param columns columns information
     * @param parameters parameters information
     */
    public PrepareResult(int statementId, ColumnInformation[] columns, ColumnInformation[] parameters) {
        this.statementId = statementId;
        this.columns = columns;
        this.parameters = parameters;
    }

    public void setAddToCache() {
        inCache.set(true);
    }

    public void setRemoveFromCache() {
        inCache.set(false);
    }

    //for debug
    public boolean inCache() {
        return inCache.get();
    }

    /**
     * Increment share counter.
     * @return true if can be used (is not been deallocate).
     */
    public synchronized boolean incrementShareCounter() {
        if (isBeingDeallocate) {
            return false;
        }
        shareCounter++;
        return true;
    }

    public synchronized void decrementShareCounter() {
        shareCounter--;
    }

    /**
     * Asked if can be deallocate (is not shared in other statement and not in cache)
     * Set deallocate flag to true if so.
     *
     * @return true if can be deallocate
     */
    public synchronized boolean canBeDeallocate() {
        if (shareCounter > 0 || isBeingDeallocate) {
            return false;
        }
        if (!inCache.get()) {
            isBeingDeallocate = true;
            return true;
        }
        return false;
    }

    //for unit test
    public synchronized int getShareCounter() {
        return shareCounter;
    }

    public int getStatementId() {
        return statementId;
    }

    public ColumnInformation[] getColumns() {
        return columns;
    }

    public ColumnInformation[] getParameters() {
        return parameters;
    }
}
