/*
 * Copyright (c) 2013 Big Switch Networks, Inc.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.statistics.northbound;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.reader.NodeTableStatistics;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class TableStatistics {
    @XmlElement
    private Node node;
    @XmlElement(name="tableStat")
    private List<NodeTableStatistics> tableStats;

    // To satisfy JAXB
    @SuppressWarnings("unused")
    private TableStatistics() {
    }

    public TableStatistics(Node node, List<NodeTableStatistics> tableStats) {
        super();
        this.node = node;
        this.tableStats = tableStats;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public List<NodeTableStatistics> getTableStats() {
        return tableStats;
    }

    public void setTableStats(List<NodeTableStatistics> tableStats) {
        this.tableStats = tableStats;
    }

}
