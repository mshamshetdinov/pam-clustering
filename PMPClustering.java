package com.company;

import Jama.Matrix;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

import org.gephi.graph.api.Node;

public class PMPClustering {
    public static UndirectedGraph readFromBenchmarkFile(String fileName, GraphModel graphModel)
    {
        UndirectedGraph graph = graphModel.getUndirectedGraph();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\t");
                int vertexA = Integer.parseInt(tokens[0]);
                int vertexB = Integer.parseInt(tokens[1]);
                Node nodeA = graph.getNode(String.valueOf(vertexA));
                Node nodeB = graph.getNode(String.valueOf(vertexB));
                if ( nodeA == null)
                {
                    nodeA = graph.getGraphModel().factory().newNode(String.valueOf(vertexA));
                    nodeA.getNodeData().setLabel(String.valueOf(vertexA));
                    graph.addNode(nodeA);
                }
                if (nodeB == null)
                {
                    nodeB = graph.getGraphModel().factory().newNode(String.valueOf(vertexB));
                    nodeB.getNodeData().setLabel(String.valueOf(vertexB));
                    graph.addNode(nodeB);
                }
                graph.addEdge(nodeA, nodeB);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return graph;
    }

    public Matrix calculateShortestPath(Graph graph) {

        final HashMap <Edge, HashMap <Edge, Integer> > edgeDistanceMap = new HashMap <Edge, HashMap <Edge, Integer> >(graph.getEdgeCount());

        //calculate the distances between all pairs of edges and put its into the edgeDistanceMap

        int n = graph.getEdgeCount();
        Matrix d = new Matrix(n, n);

        for (Edge startEdge: graph.getEdges()) {

            HashMap <Edge, Integer> smallEdgeDistanceMap = new HashMap <Edge, Integer> (graph.getEdgeCount());

            List <Edge> nextStage = new ArrayList <Edge> ();
            nextStage.add(startEdge);
            int stage = 0;
            smallEdgeDistanceMap.put(startEdge, stage);
            d.set(startEdge.getId() - 1, startEdge.getId() - 1, 0.0);
            while (!nextStage.isEmpty()) {
                stage++;
                List <Edge> prev = new ArrayList(nextStage);
                nextStage.clear();

                for (Edge currEdge: prev) {
                    for (Edge nextEdge: graph.getEdges(currEdge.getSource())  ) {
                        if (smallEdgeDistanceMap.get(nextEdge) == null) {
                            nextStage.add(nextEdge);
                            smallEdgeDistanceMap.put(nextEdge, stage);
                            d.set(startEdge.getId() - 1 , nextEdge.getId() - 1, stage);
                        }
                    }

                    for (Edge nextEdge: graph.getEdges(currEdge.getTarget())  ) {
                        if (smallEdgeDistanceMap.get(nextEdge) == null) {
                            nextStage.add(nextEdge);
                            smallEdgeDistanceMap.put(nextEdge, stage);
                            d.set(startEdge.getId() - 1 , nextEdge.getId() - 1 , stage);
                        }
                    }
                }
            } //while

            edgeDistanceMap.put(startEdge, smallEdgeDistanceMap);
        }

        return d;
    }
    public Matrix calculateCommute(Graph graph) {
        final HashMap <Edge, HashMap <Edge, Double> > edgeDistanceMap = new HashMap <Edge, HashMap <Edge, Double> >(graph.getEdgeCount());

        //claculate lineral graph
        HashMap <Edge, Integer> edgeMap = new HashMap();

        //fill the edge map
        for (Edge edge: graph.getEdges())
            edgeMap.put(edge, edge.getId());

        //--------------------start of distance maxtrix calculation ---------------------------------
        double a[][] = Utils.buildLineralGraph(graph);

        int n = graph.getEdgeCount(); //now we have

        double V = 0;
        Matrix D = new Matrix(n, n);
        for (int i=0; i < n; i++) {
            edgeDistanceMap.put(graph.getEdge(i+1), new HashMap());
            double degree = 0;
            for (int j=0; j < n; j++  ) {
                degree = degree+a[i][j];
                V = V + a[i][j];
            }
            D.set(i, i, degree);
            System.out.println(degree);
        }

        Matrix A = new Matrix(a);
        Matrix L = D.minus(A);

        L = Utils.pinv(L);

        Matrix d = new Matrix(n, n);

        for (int i = 0; i < n; i++) {

            Matrix ei = new Matrix(1,n);
            ei.set(0, i, 1);
            for (int j = 0; j < n; j++  ) {
                Matrix ej = new Matrix(1,n);
                ej.set(0, j, 1);

                Matrix left = ei.minus(ej);

                left = left.times(V);

                left =  left.times(L);

                Matrix right = ei.transpose().minus(ej.transpose());

                left = left.times(right);

                d.set(i, j, Math.sqrt(left.get(0, 0)));
                edgeDistanceMap.get(graph.getEdge(i+1)).put(graph.getEdge(j+1),    Math.sqrt(left.get(0, 0))  );
                edgeDistanceMap.get(graph.getEdge(j+1)).put(graph.getEdge(i+1),    Math.sqrt(left.get(0, 0))  );
            }
        }
        return d;
    }

    public void doClustering(String fileName, String communitiesFile, int clustersNumber, boolean benchmark, boolean commute) {
        String[] split = fileName.split("/");
        String suffix = split[split.length - 1];
        suffix = suffix.split("\\.")[0];

        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        Container container = null;
        Graph graph;
        if (benchmark) {
            graph = PMPClustering.readFromBenchmarkFile(fileName, Lookup.getDefault().lookup(GraphController.class).getModel(workspace));
            System.out.println("Edge count: " + graph.getEdgeCount());

            for (Edge edge : graph.getEdges()) {
                System.out.print(edge.getSource().getNodeData().getLabel() + " ");
                System.out.println(edge.getTarget().getNodeData().getLabel());
            }
        }
        else {
            //Import file
            try {
                File file = new File(fileName);

                container = importController.importFile(file);
                container.getLoader().setEdgeDefault(EdgeDefault.UNDIRECTED);
                container.setAllowAutoNode(false);  //Don't create missing nodes
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }

            //Append imported data to GraphAPI
            importController.process(container, new DefaultProcessor(), workspace);

            //Get a graph model - it exists because we have a workspace
            GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
            graph = graphModel.getGraph();

            try {
                PrintWriter writer = new PrintWriter("network_format" + suffix + ".dat", "UTF-8");
                for (Edge edge : graph.getEdges()) {
                    writer.println(String.valueOf(edge.getTarget().getId()) + "\t" + String.valueOf(edge.getSource().getId()));
                }
                writer.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }

        }

        Matrix d;
        if (commute) d = calculateCommute(graph);
        else d = calculateShortestPath(graph);

        d.print(2,2);

        System.out.println(graph.getEdge(1).getId());

        System.out.println("edgeDistanceMap has been calculated\n");

        //--------------------------------------------end of matrix calculation----------------------------------------------------

        //edge clustering
        PBPolynomial sol = new PBPolynomial();
        HashMap<Integer/*Edge*/, Vector<Integer>> edge_clusters = sol.PMedianClustering(clustersNumber, d, suffix);

        //results of clustering
        HashMap<Integer/*Node*/, HashMap<Integer, Integer>/*Clusters*/> node_clusters = new HashMap<>();
        HashMap<Integer/*Cluster*/, Set<Integer>/*Nodes*/> format_clusters = new HashMap<>();
        Vector<Integer> c = new Vector<>(edge_clusters.keySet());
        c.sort(Integer::compareTo);
        System.out.println("Sorted clusters indices: " + Arrays.asList(c));
        for (Integer cluster: edge_clusters.keySet()) {
            Vector<Integer> edges = edge_clusters.get(cluster);
            for(Integer edge: edges) {
                Edge e = graph.getEdge(edge);
                Node target = e.getTarget();
                Node source = e.getSource();

                Integer targetId, sourceId;
                if(benchmark) {
                    targetId = Integer.parseInt(target.getNodeData().getLabel());
                    sourceId = Integer.parseInt(source.getNodeData().getLabel());
                }
                else {
                    targetId = target.getId();
                    sourceId = source.getId();
                }
                node_clusters.computeIfAbsent(targetId, k -> new HashMap<>());
                node_clusters.computeIfAbsent(sourceId, k -> new HashMap<>());
                if (node_clusters.get(targetId).get(c.indexOf(cluster) + 1) == null)
                    node_clusters.get(targetId).put(c.indexOf(cluster) + 1, 1);
                else node_clusters.get(targetId).put(c.indexOf(cluster) + 1, node_clusters.get(targetId).get(c.indexOf(cluster) + 1) + 1 );

                if (node_clusters.get(sourceId).get(c.indexOf(cluster) + 1) == null)
                    node_clusters.get(sourceId).put(c.indexOf(cluster) + 1, 1);
                else node_clusters.get(sourceId).put(c.indexOf(cluster) + 1, node_clusters.get(sourceId).get(c.indexOf(cluster) + 1) + 1 );

                format_clusters.computeIfAbsent(c.indexOf(cluster) + 1,  k -> new TreeSet<>());
                format_clusters.get(c.indexOf(cluster) + 1).add(targetId);
                format_clusters.get(c.indexOf(cluster) + 1).add(sourceId);
            }
        }

        System.out.println("PMP clusters: " + Arrays.asList(node_clusters));
        System.out.println("Clusters: " + Arrays.asList(format_clusters));

        for (Integer key : node_clusters.keySet()) {
            if (node_clusters.get(key).size() > 1) {
                int sum = 0;
                for (Integer val : node_clusters.get(key).values()) {
                    sum += val;
                }
                System.out.print("Node: " + String.valueOf(key) + " ");
                for (Integer k : node_clusters.get(key).keySet()) {
                    DecimalFormat df = new DecimalFormat("####0.00");
                    System.out.print(String.valueOf(k) + " " + df.format(node_clusters.get(key).get(k) * 100 / (double) sum) + "% ");
                }
                System.out.println();
            }
        }
        sol.writeForMetrics(format_clusters, "pmp_" + suffix + ".dat");
        if(benchmark)
        {
            HashMap<Integer, Set<Integer>> cl = sol.formatClustersFile(communitiesFile);
            sol.writeForMetrics(cl, "truth_" + suffix + ".dat");
            System.out.println("Clusters: " + Arrays.asList(cl));
        }

        float [] r = {1.0f, 0.0f, 0.8f, 0.0f, 0, 1, 1, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f };
        float [] g = {0.0f, 0.6f, 0.0f, 0.6f, 1, 0, 1, 0.7f, 0.6f, 0.4f, 0.5f, 0.3f };
        float [] b = {0.0f, 1.0f, 0.8f, 0.2f, 1, 1, 1, 0.6f, 0.7f, 0.3f, 0.4f, 0.5f };

        Set <Node> coloredNodes = new HashSet();

        int clusterNumber = 0;
        for (Vector <Integer> cluster: edge_clusters.values()) {
            for (Integer edge_id: cluster) {
                Edge edge = graph.getEdge(edge_id);
                edge.getEdgeData().setColor(r[clusterNumber], g[clusterNumber], b[clusterNumber]);
                if (coloredNodes.contains(edge.getSource())) {
                    float rr = edge.getSource().getNodeData().r();
                    float gg = edge.getSource().getNodeData().g();
                    float bb = edge.getSource().getNodeData().b();

                    edge.getSource().getNodeData().setColor((rr + r[clusterNumber])/2.0f, (gg + g[clusterNumber])/2.0f, (bb + b[clusterNumber]) / 2.0f);
                } else {
                    coloredNodes.add(edge.getSource());
                    edge.getSource().getNodeData().setColor(r[clusterNumber], g[clusterNumber], b[clusterNumber]);

                }

                if (coloredNodes.contains(edge.getTarget())) {
                    float rr = edge.getTarget().getNodeData().r();
                    float gg = edge.getTarget().getNodeData().g();
                    float bb = edge.getTarget().getNodeData().b();

                    edge.getTarget().getNodeData().setColor((rr + r[clusterNumber])/2.0f, (gg + g[clusterNumber])/2.0f, (bb + b[clusterNumber]) / 2.0f);
                } else {
                    coloredNodes.add(edge.getTarget());
                    edge.getTarget().getNodeData().setColor(r[clusterNumber], g[clusterNumber], b[clusterNumber]);

                }

            }
            clusterNumber++;
        }

        for (Node node: graph.getNodes()) {
            node.getNodeData().setLabel(node.getNodeData().getLabel());
        }

        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
            ec.exportFile(new File("graph_" + suffix + ".gexf"));
        } catch (IOException ex) {
            throw new Error(ex);
        }

        System.out.println("The work has been done successfully!");
    }

    public static void main(String[] args) {
        PMPClustering clustering = new PMPClustering();
        clustering.doClustering("karate.gml", "communities.dat", 2, false, true);
    }
} //class

