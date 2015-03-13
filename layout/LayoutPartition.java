/* vim: set ts=2: */
/**
 * Copyright (c) 2006 The Regents of the University of California.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *   1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions, and the following disclaimer.
 *   2. Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions, and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *   3. Redistributions must acknowledge that this software was
 *      originally developed by the UCSF Computer Graphics Laboratory
 *      under support by the NIH National Center for Research Resources,
 *      grant P41-RR01081.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package csplugins.layout;

import cytoscape.logger.CyLogger;

import csplugins.layout.LayoutEdge;
import csplugins.layout.LayoutNode;
import csplugins.layout.Profile;
import csplugins.layout.EdgeWeighter;

import cytoscape.util.intr.IntIntHash;
import cytoscape.util.intr.IntObjHash;

import cytoscape.*;

import cytoscape.view.*;

import giny.view.*;

import java.awt.Dimension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;
import java.util.Collection;


/**
 * The LayoutPartition class contains all of the information about a
 * single graph partition, where a partition is defined as all nodes
 * in a graph that connect only to each other.  This class also provides
 * static methods that are used to partition an existing graph.
 *
 * @author <a href="mailto:scooter@cgl.ucsf.edu">Scooter Morris</a>
 * @version 0.9
 */
public class LayoutPartition {
    protected ArrayList<LayoutNode> nodeList;
    protected ArrayList<LayoutEdge> edgeList;
    protected static HashMap<CyNode, LayoutNode> nodeToLayoutNode = null;
    protected static IntIntHash nodesSeenMap = null;
    protected static IntIntHash edgesSeenMap = null;
    protected int nodeIndex = 0;
    protected int partitionNumber = 0;
    protected EdgeWeighter edgeWeighter = null;

    // Keep track of the node min and max values
    protected double maxX = -100000;
    protected double maxY = -100000;
    protected double minX = 100000;
    protected double minY = 100000;
    protected double width = 0;
    protected double height = 0;

    // Keep track of average location
    protected double averageX = 0;
    protected double averageY = 0;

    // Keep track of the number of locked nodes we have in
    // this partition
    protected int lockedNodes = 0;

    protected CyLogger logger = null;

    // protected constants
    protected static final int m_NODE_HAS_NOT_BEEN_SEEN = 0;
    protected static final int m_NODE_HAS_BEEN_SEEN = 1;

    /**
     * LayoutPartition: use this constructor to create an empty LayoutPartition.
     *
     * @param nodeCount    The number of nodes in the new partition.
     * @param edgeCount    The number of edges in the new partition.
     */
    public LayoutPartition(int nodeCount, int edgeCount) {
	logger = CyLogger.getLogger(LayoutPartition.class);
	nodeList = new ArrayList<LayoutNode>(nodeCount);
	edgeList = new ArrayList<LayoutEdge>(edgeCount);
	partitionNumber = 1;

	if (nodeToLayoutNode == null)
	    nodeToLayoutNode = new HashMap<CyNode,LayoutNode>(nodeCount);
    }

    /**
     * LayoutPartition: use this constructor to create a LayoutPartition that
     * includes the entire network.
     *
     * @param network the CyNetwork to include
     * @param networkView the CyNetworkView to use
     * @param nodeSet the nodes to be considered
     * @param edgeWeighter the weighter to use for edge weighting
     */
    public LayoutPartition(CyNetwork network, CyNetworkView networkView, Collection<CyNode>nodeSet,
			   EdgeWeighter edgeWeighter) {
	initialize(network,networkView,nodeSet,edgeWeighter);
    }


    /**
     * LayoutPartition: use this constructor to create a LayoutPartition that
     * includes the entire network.
     *
     * @param network the CyNetwork to include
     * @param networkView the CyNetworkView to use
     * @param selectedOnly if true, only include selected nodes in the partition
     * @param edgeWeighter the weighter to use for edge weighting
     */
    public LayoutPartition(CyNetwork network, CyNetworkView networkView, boolean selectedOnly,
			   EdgeWeighter edgeWeighter) {
	if (selectedOnly) {
	    initialize(network,networkView,(Collection<CyNode>)network.getSelectedNodes(),edgeWeighter);
	} else {
	    initialize(network,networkView,network.nodesList(),edgeWeighter);
	}
    }

    protected void initialize(CyNetwork network, CyNetworkView networkView, Collection<CyNode>nodeSet,
			      EdgeWeighter edgeWeighter) {

	logger = CyLogger.getLogger(LayoutPartition.class);

	this.edgeWeighter = edgeWeighter;

	// Initialize
	nodeList = new ArrayList<LayoutNode>(network.getNodeCount());
	edgeList = new ArrayList<LayoutEdge>(network.getEdgeCount());

	if (nodeToLayoutNode == null)
	    nodeToLayoutNode = new HashMap<CyNode,LayoutNode>(network.getNodeCount());

	// Now, walk the iterators and fill in the values
	nodeListInitialize(network, networkView, nodeSet);
	edgeListInitialize(network, networkView);
	trimToSize();
	partitionNumber = 1;
    }

    /**
     * Set the EdgeWeighter to use for this partition.  The EdgeWeighter should be
     * shared by all partitions in the same graph to avoid contrary scaling problems.
     *
     * @param edgeWeighter the weighter to use for edge weighting
     */
    public void setEdgeWeighter(EdgeWeighter edgeWeighter) {
	this.edgeWeighter = edgeWeighter;
    }

    /**
     * Add a node to this partition.
     *
     * @param nv the NodeView of the node to add
     * @param locked a boolean value to determine if this node is locked or not
     */
    protected void addNode(NodeView nv, boolean locked) {
	CyNode node = (CyNode) nv.getNode();
	LayoutNode v = new LayoutNodeImpl(nv, nodeIndex++);
	nodeList.add(v);
	nodeToLayoutNode.put(node, v);

	if (locked) {
	    v.lock();
	    lockedNodes++;
	} else {
	    updateMinMax(nv.getXPosition(), nv.getYPosition());
	    this.width += nv.getWidth();
	    this.height += nv.getHeight();
	}
    }

    /**
     * Add an edge to this partition assuming that the source and target
     * nodes are not yet known.
     *
     * @param edge    the CyEdge to add to the partition
     */
    protected void addEdge(CyEdge edge) {
	LayoutEdge newEdge = new LayoutEdge(edge);
	updateWeights(newEdge);
	edgeList.add(newEdge);
    }

    /**
     * Add an edge to this partition assuming that the source and target
     * nodes <em>are</em> known.
     *
     * @param edge    the CyEdge to add to the partition
     * @param v1    the LayoutNode of the edge source
     * @param v2    the LayoutNode of the edge target
     */
    protected void addEdge(CyEdge edge, LayoutNode v1, LayoutNode v2) {
	LayoutEdge newEdge = new LayoutEdge(edge, v1, v2);
	updateWeights(newEdge);
	edgeList.add(newEdge);
    }

    /**
     * Randomize the graph locations.
     */
    public void randomizeLocations() {
	// Get a seeded pseudo random-number generator
	Date today = new Date();
	Random random = new Random(today.getTime());
	// Reset our min and max values
	resetNodes();

	for (LayoutNode node: nodeList) {

	    if (!node.isLocked()) {
		double x = random.nextDouble() * width;
		double y = random.nextDouble() * height;
		node.setLocation(x, y);
		updateMinMax(x, y);
	    } else {
		updateMinMax(node.getX(), node.getY());
	    }
	}
    }

    /**
     * Move the node to its current X and Y values.  This is a wrapper
     * to LayoutNode's moveToLocation, but has the property of updating
     * the current min and max values for this partition.
     *
     * @param node the LayoutNode to move
     */
    public void moveNodeToLocation(LayoutNode node) {
	// We provide this routine so that we can keep our
	// min/max values updated
	if (node.isLocked())
	    return;

	node.moveToLocation();
	updateMinMax(node.getX(), node.getY());
    }

    /**
     * Convenience routine to update the source and target for all of the
     * edges in a partition.  This is useful when the algorithm used makes it
     * difficult to record source and target until it has completed.
     */
    public void fixEdges() {
	for (LayoutEdge lEdge: edgeList) {
	    // Get the underlying edge
	    CyEdge edge = lEdge.getEdge();
	    CyNode target = (CyNode) edge.getTarget();
	    CyNode source = (CyNode) edge.getSource();

	    if (nodeToLayoutNode.containsKey(source) && nodeToLayoutNode.containsKey(target)) {
		// Add the connecting nodes
		lEdge.addNodes((LayoutNode) nodeToLayoutNode.get(source),
			       (LayoutNode) nodeToLayoutNode.get(target));
	    }
	}
    }

    /**
     * Calculate and set the edge weights.  Note that this will delete
     * edges from the calculation (not the graph) when certain conditions
     * are met.
     */
    public void calculateEdgeWeights() {

	// Use a ListIterator so that we can modify the list
	// as we go
	ListIterator<LayoutEdge>iter = edgeList.listIterator();
	while (iter.hasNext()) {
	    LayoutEdge edge = iter.next();

	    // If we're only dealing with selected nodes, drop any edges
	    // that don't have any selected nodes
	    if (edge.getSource().isLocked() && edge.getTarget().isLocked()) {
		iter.remove();
	    } else if (edgeWeighter != null && edgeWeighter.normalizeWeight(edge) == false)
		iter.remove();

	    //logger.debug("Edge "+edge.getEdge().getIdentifier()+" now has weight "+edge.getWeight());
	    // logger.info( edge.toString() + " now has weight "+edge.getWeight());
	}
    }

    /**
     * Return the size of this partition, which is defined as the number of
     * nodes that it contains.
     *
     * @return    partition size
     */
    public int size() {
	return nodeList.size();
    }

    /**
     * Return the list of LayoutNodes within this partition.
     *
     * @return    List of LayoutNodes
     * @see LayoutNode
     */
    public List<LayoutNode> getNodeList() {
	return nodeList;
    }

    /**
     * Return the list of LayoutEdges within this partition.
     *
     * @return    List of LayoutEdges
     * @see LayoutEdge
     */
    public List<LayoutEdge> getEdgeList() {
	return edgeList;
    }

    /**
     * Return an iterator over all of the LayoutNodes in this parition
     *
     * @return Iterator over the list of LayoutNodes
     * @see LayoutNode
     */
    public Iterator<LayoutNode> nodeIterator() {
	return nodeList.iterator();
    }

    /**
     * Return an iterator over all of the LayoutEdges in this parition
     *
     * @return Iterator over the list of LayoutEdges
     * @see LayoutEdge
     */
    public Iterator<LayoutEdge> edgeIterator() {
	return edgeList.iterator();
    }

    /**
     * Return the number of nodes in this partition
     *
     * @return number of nodes in the partition
     */
    public int nodeCount() {
	return nodeList.size();
    }

    /**
     * Return the number of edges in this partition
     *
     * @return number of edges in the partition
     */
    public int edgeCount() {
	return edgeList.size();
    }

    /**
     * Return the maximum X location of all of the LayoutNodes
     *
     * @return maximum X location
     */
    public double getMaxX() {
	return maxX;
    }

    /**
     * Return the maximum Y location of all of the LayoutNodes
     *
     * @return maximum Y location
     */
    public double getMaxY() {
	return maxY;
    }

    /**
     * Return the minimum X location of all of the LayoutNodes
     *
     * @return minimum X location
     */
    public double getMinX() {
	return minX;
    }

    /**
     * Return the minimum Y location of all of the LayoutNodes
     *
     * @return minimum Y location
     */
    public double getMinY() {
	return minY;
    }

    /**
     * Return the total width of all of the LayoutNodes
     *
     * @return total width of all of the LayoutNodes
     */
    public double getWidth() {
	return width;
    }

    /**
     * Return the total height of all of the LayoutNodes
     *
     * @return total height of all of the LayoutNodes
     */
    public double getHeight() {
	return height;
    }

    /**
     * Return the partition number of this partition
     *
     * @return partition number
     */
    public int getPartitionNumber() {
	return partitionNumber;
    }

    /**
     * Set the partition number of this partition
     *
     * @param part partition number
     */
    public void setPartitionNumber(int part) {
	partitionNumber = part;
    }

    /**
     * Return the number of locked nodes within this parititon
     *
     * @return number of locked nodes in partition
     */
    public int lockedNodeCount() {
	return lockedNodes;
    }

    /**
     * Return the average location of the nodes in this partition
     *
     * @return average location of the nodes as a Dimension
     * @see Dimension
     */
    public Dimension getAverageLocation() {
	int nodes = nodeCount() - lockedNodes;
	Dimension result = new Dimension();
	result.setSize(averageX / nodes, averageY / nodes);

	return result;
    }

    /**
     * Recalcultares max, min and average values from scratch 
     */
    public void recalculateStatistics() {
	this.resetNodes();
	for (LayoutNode n: this.getNodeList()) 
	    if (!n.isLocked()) 
		updateMinMax(n.getX(), n.getY());
    }

    /**
     * Offset all of the nodes in the partition by a fixed
     * amount.  This is used by algorithms of offset each
     * partition after laying it out.
     *
     * @param xoffset the amount to offset in the X direction
     * @param yoffset the amount to offset in the Y direction
     */
    public void offset(double xoffset, double yoffset) {
	double myMinX = this.minX;
	double myMinY = this.minY;
	resetNodes();

	for (LayoutNode node: nodeList) {
	    node.increment(xoffset - myMinX, yoffset - myMinY);
	    moveNodeToLocation(node);
	}
    }

    /**
     * Reset routines
     */

    /**
     * Reset all of the data maintained for the LayoutNodes
     * contained within this partition, including the min, max
     * and average x and y values.
     */
    public void resetNodes() {
	maxX = -100000;
	maxY = -100000;
	minX = 100000;
	minY = 100000;
	averageX = 0;
	averageY = 0;
    }

    /**
     * Protected routines
     */
    protected void nodeListInitialize(CyNetwork network, CyNetworkView networkView,
				      Collection<CyNode> nodeSet) {
	int nodeIndex = 0;
	this.nodeList = new ArrayList<LayoutNode>(network.getNodeCount());
	    
	Iterator<NodeView>iter = networkView.getNodeViewsIterator();
	while (iter.hasNext()) {
	    NodeView nv = (NodeView) iter.next();
	    CyNode node = (CyNode) nv.getNode();
		
	    if (!nodeSet.contains(node)) {
		addNode(nv, true);
	    } else {
		addNode(nv, false);
	    }
	}
    }
    
    protected void edgeListInitialize(CyNetwork network, CyNetworkView networkView) {
	for (CyEdge edge: (List<CyEdge>)network.edgesList()) {

	    CyNode source = (CyNode) edge.getSource();
	    CyNode target = (CyNode) edge.getTarget();

	    if (source == target)
		continue;

	    LayoutNode v1 = (LayoutNode) nodeToLayoutNode.get(source);
	    LayoutNode v2 = (LayoutNode) nodeToLayoutNode.get(target);

	    // Do we care about this edge?
	    if (v1.isLocked() || v2.isLocked())
		continue; // no, ignore it

	    EdgeView ev = networkView.getEdgeView(edge);
	    ev.clearBends();

	    addEdge(edge, v1, v2);
	}
    }

    /**
     * Space saving convenience function to trim the internal arrays to fit the
     * contained data.  Useful to call this after a partition has been created
     * and filled.  This is used by the static method LayoutPartition.partition
     */
    protected void trimToSize() {
	nodeList.trimToSize();
	edgeList.trimToSize();
    }

    protected void updateMinMax(double x, double y) {
	minX = Math.min(minX, x);
	minY = Math.min(minY, y);
	maxX = Math.max(maxX, x);
	maxY = Math.max(maxY, y);
	averageX += x;
	averageY += y;
    }

    protected void updateWeights(LayoutEdge newEdge) {
	if (edgeWeighter != null) {
	    edgeWeighter.setWeight(newEdge);
	}

	// logger.debug("Updating "+newEdge);
    }

    // Static routines

    /**
     * Partition the graph -- this builds the LayoutEdge and LayoutNode
     * arrays as a byproduct.  The algorithm for this was taken from
     * algorithms/graphPartition/SGraphPartition.java.
     *
     * @param network the CyNetwork containing the graph
     * @param networkView the CyNetworkView representing the graph
     * @param selectedOnly only consider selected nodes
     * @param edgeWeighter the weighter to use for edge weighting
     * @return a List of LayoutPartitions
     */
    public static List<LayoutPartition> partition(CyNetwork network, CyNetworkView networkView,
						  boolean selectedOnly, EdgeWeighter edgeWeighter) {

	if (selectedOnly) {
	    return partition(network,networkView,network.getSelectedNodes(),edgeWeighter);
	}

	return partition(network,networkView,network.nodesList(),edgeWeighter);

    }

    /**
     * Partition the graph -- this builds the LayoutEdge and LayoutNode
     * arrays as a byproduct.  The algorithm for this was taken from
     * algorithms/graphPartition/SGraphPartition.java.
     *
     * @param network the CyNetwork containing the graph
     * @param networkView the CyNetworkView representing the graph
     * @param nodeSet the set of nodes to consider
     * @param edgeWeighter the weighter to use for edge weighting
     * @return a List of LayoutPartitions
     */
    public static List<LayoutPartition> partition(CyNetwork network, CyNetworkView networkView,
						  Collection<CyNode> nodeSet, EdgeWeighter edgeWeighter) {
	ArrayList<LayoutPartition> partitions = new ArrayList<LayoutPartition>();

	nodesSeenMap = new IntIntHash();
	edgesSeenMap = new IntIntHash();

	IntObjHash nodesToViews = new IntObjHash();
	nodeToLayoutNode = new HashMap<CyNode,LayoutNode>(network.getNodeCount());

	// Initialize the maps
	Iterator nodeViewIter = networkView.getNodeViewsIterator();

	while (nodeViewIter.hasNext()) {
	    NodeView nv = (NodeView) nodeViewIter.next();
	    int node = nv.getNode().getRootGraphIndex();
	    nodesSeenMap.put(-node, m_NODE_HAS_NOT_BEEN_SEEN);
	    nodesToViews.put(-node, nv);
	}

	for (CyEdge edge: (List<CyEdge>)network.edgesList()) {
	    int edgeIndex = edge.getRootGraphIndex();
	    edgesSeenMap.put(-edgeIndex, m_NODE_HAS_NOT_BEEN_SEEN);
	}

	// OK, now traverse the graph
	for (CyNode node: nodeSet) {
	    int nodeIndex = node.getRootGraphIndex();

	    // Have we seen this already?
	    if (nodesSeenMap.get(-nodeIndex) == m_NODE_HAS_BEEN_SEEN)
		continue;

	    // Nope, first time
	    LayoutPartition part = new LayoutPartition(network.getNodeCount(),
						       network.getEdgeCount());
	    // Set the edge weighter
	    part.setEdgeWeighter(edgeWeighter);

	    nodesSeenMap.put(-nodeIndex, m_NODE_HAS_BEEN_SEEN);

	    // Traverse through all connected nodes
	    traverse(network, networkView, nodesToViews, node, part);

	    // Done -- finalize the parition
	    part.trimToSize();

	    // Finally, now that we're sure we've touched all of our
	    // nodes.  Fix up our edgeLayout list to have all of our
	    // layoutNodes
	    part.fixEdges();

	    partitions.add(part);
	}

	// Now sort the partitions based on the partition's node count
	LayoutPartition[] a = new LayoutPartition[1];
	LayoutPartition[] parts = partitions.toArray(a);
	Arrays.sort(parts,
		    new Comparator<LayoutPartition>() {
			public int compare(LayoutPartition p1, LayoutPartition p2) {
			    return (p2.size() - p1.size());
			}

			public boolean equals(LayoutPartition obj) {
			    return false;
			}
		    });

	return Arrays.asList(parts);
    }

    /**
     * This method traverses nodes connected to the specified node.
     * @param network                The CyNetwork we are laying out
     * @param networkView        The CyNetworkView we are laying out
     * @param nodesToViews        A map that maps between nodes and views
     * @param node                        The node to search for connected nodes.
     * @param partition the partition we're laying out
     */
    protected static void traverse(CyNetwork network, CyNetworkView networkView,
				   IntObjHash nodesToViews, CyNode node,
				   LayoutPartition partition) {
	int nodeIndex = node.getRootGraphIndex();

	// Get the nodeView
	NodeView nv = (NodeView) nodesToViews.get(-nodeIndex);

	// Add this node to the partition
	partition.addNode(nv, false);

	// Get the list of edges connected to this node
	int[] incidentEdges = network.getAdjacentEdgeIndicesArray(nodeIndex, true, true, true);

	// Iterate through each connected edge
	for (int i = 0; i < incidentEdges.length; i++) {
	    // Get the actual edge
	    CyEdge incidentEdge = (CyEdge) network.getEdge(incidentEdges[i]);

	    int edgeIndex = incidentEdge.getRootGraphIndex();

	    // Have we already seen this edge?
	    if (edgesSeenMap.get(-edgeIndex) == m_NODE_HAS_BEEN_SEEN) {
		// Yes, continue since it means we *must* have seen both sides
		continue;
	    }

	    edgesSeenMap.put(-edgeIndex, m_NODE_HAS_BEEN_SEEN);

	    // Make sure we clean up after any previous layouts
	    EdgeView ev = networkView.getEdgeView(incidentEdge);
	    ev.clearBends();

	    // Add the edge to the partition
	    partition.addEdge(incidentEdge);

	    // Determine the node's index that is on the other side of the edge
	    CyNode otherNode;

	    if (incidentEdge.getSource() == node) {
		otherNode = (CyNode) incidentEdge.getTarget();
	    } else {
		otherNode = (CyNode) incidentEdge.getSource();
	    }

	    int incidentNodeIndex = otherNode.getRootGraphIndex();

	    // Have we seen the connecting node yet?
	    if (nodesSeenMap.get(-incidentNodeIndex) == m_NODE_HAS_NOT_BEEN_SEEN) {
		// Mark it as having been seen
		nodesSeenMap.put(-incidentNodeIndex, m_NODE_HAS_BEEN_SEEN);

		// Traverse through this one
		traverse(network, networkView, nodesToViews, otherNode, partition);
	    }
	}
    }
}
