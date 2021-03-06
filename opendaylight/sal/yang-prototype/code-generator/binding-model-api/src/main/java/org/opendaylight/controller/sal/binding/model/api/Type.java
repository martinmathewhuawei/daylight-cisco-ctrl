/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.binding.model.api;

public interface Type {
    /**
     * Returns name of the package that interface belongs to.
     * 
     * @return name of the package that interface belongs to
     */
    public String getPackageName();

    /**
     * Returns name of the interface.
     * 
     * @return name of the interface.
     */
    public String getName();
}
