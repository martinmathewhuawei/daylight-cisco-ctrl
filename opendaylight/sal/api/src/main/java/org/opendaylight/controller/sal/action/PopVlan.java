
/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.action;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Pop vlan action (strip the outermost 802.1q header)
 *
 *
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)

public class PopVlan extends Action {

    public PopVlan() {
        type = ActionType.POP_VLAN;
    }
}
