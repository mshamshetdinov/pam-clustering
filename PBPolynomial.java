package com.company;
import lpsolve.*;
import Jama.Matrix;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;


public class PBPolynomial {

    public static final boolean DEBUG_MODE = false;

    public HashMap<Integer, Set<Integer>> formatClustersFile(String fileName)
    {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            HashMap<Integer, Set<Integer>> clusters = new HashMap<>();
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\t");
                int vertex = Integer.parseInt(tokens[0]);
                String[] comms = tokens[1].split(" ");
                for (String s: comms) {
                    int cl = Integer.parseInt(s);
                    clusters.computeIfAbsent(cl, k -> new TreeSet<>());
                    clusters.get(cl).add(vertex);
                }
            }
            if(DEBUG_MODE) System.out.println("Clusters: " + Arrays.asList(clusters));
            return clusters;
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public void writeForMetrics(HashMap<Integer, Set<Integer>> clusters, String fileName)
    {
        try{
            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            for(Set<Integer> cluster: clusters.values()){
                int size = cluster.size();
                int count = 0;
                for(Integer node: cluster){
                    count++;
                    writer.print(String.valueOf(node) + (count==size?"":" "));
                }
                writer.println();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Integer[] getIndicesInSortedArr(double[] array) {
        TreeMap<Double, List<Integer>> map = new TreeMap<Double, List<Integer>>();
        for (int i = 0; i < array.length; i++) {
            List<Integer> ind = map.get(array[i]);
            if (ind == null) {
                ind = new ArrayList<Integer>();
                map.put(array[i], ind);
            }
            ind.add(i);
        }

        List<Integer> indices = new ArrayList<Integer>();
        for (List<Integer> arr : map.values()) {
            indices.addAll(arr);
        }
        return indices.toArray(new Integer[indices.size()]);
    }

    public void preprocessCosts(double[] costs, int n, int p) {
        double[] sorted = Arrays.copyOf(costs, costs.length);
        Arrays.sort(sorted);

        int permutation_index = n - p + 1;

        double permutation_value = sorted[permutation_index - 1];
        if(DEBUG_MODE) System.out.println("Perm value: " + permutation_value);

        Integer[] indicesArr = this.getIndicesInSortedArr(costs);

        for (int i = indicesArr.length - 1, count = 0; count != p; i--, count++) {
            costs[indicesArr[i]] = permutation_value;
        }
    }

    public Polynomial getPolynomial(double[] diff, Integer[] ordering, int max_degree) {
        Polynomial res = new Polynomial();
        for (int i = 0; i < diff.length; i++) {
            SortedSet<Integer> var = new TreeSet<>();
            if (i == 0) {
                var.add(0);
                res.putCoef(var, diff[i]);
            } else {
                Integer[] slice = Arrays.copyOfRange(ordering, 0, i);
                var.addAll(Arrays.asList(slice));
                //String[] str_slice =Arrays.toString(slice).split("[\\[\\]]")[1].split(", ");
                //String var = String.join("",str_slice);
                //System.out.println(var);
                if(var.size() <= max_degree) // Diffs for this degrees could be 0.0?
                    res.putCoef(var, diff[i]);
            }
        }
        return res;
    }

    public HashMap<Integer, Vector<Integer>> PMedianClustering(int p, Matrix distances, String suffix) {
        try {
            if (DEBUG_MODE) distances.print(1, 1);
            Polynomial objFunc = new Polynomial();
            int n = distances.getColumnDimension();
            int rows = distances.getRowDimension();
            System.out.println("Matrix dimensions rows: " + rows + " columns: " + n);
            for (int i = 0; i < n; ++i) {
                Matrix column = distances.getMatrix(0, rows - 1, i, i);

                double[] costsArray = column.getColumnPackedCopy();

                if(DEBUG_MODE) System.out.println("Original: " + Arrays.toString(costsArray));

                this.preprocessCosts(costsArray, rows, p);

                if(DEBUG_MODE) System.out.println("Preprocessed: " + Arrays.toString(costsArray));

                // Sorting
                double[] sorted = Arrays.copyOf(costsArray, costsArray.length);
                Arrays.sort(sorted);

                if(DEBUG_MODE) System.out.println("Sorted: " + Arrays.toString(sorted));

                //Indices in sorted array
                Integer[] indicesArr = this.getIndicesInSortedArr(costsArray);
                if(DEBUG_MODE) System.out.println("Indices: " + Arrays.toString(indicesArr)); // TODO do not forget +1 for y1 y2 and so on

                double[] diff = Arrays.copyOf(sorted, sorted.length);

                for (int j = 1; j < diff.length; j++) {
                    diff[j] = sorted[j] - sorted[j - 1];
                }
                if(DEBUG_MODE) System.out.println("Diff: " + Arrays.toString(diff));

                Integer[] normIndices = Arrays.copyOf(indicesArr, indicesArr.length);
                for (int j = 0; j < normIndices.length; j++) {
                    normIndices[j]++;
                }
                if(DEBUG_MODE) System.out.println("Norm Indices: " + Arrays.toString(normIndices));

                Polynomial a = this.getPolynomial(diff, normIndices, rows - p);
                if(DEBUG_MODE) { System.out.print("Polynomial: ");
                a.print(); }

                if(DEBUG_MODE) System.out.println(new String(new char[80]).replace("\0", "-"));
                objFunc.plus(a);
                System.out.println(String.valueOf(i+1) + " / " + String.valueOf(n));
            }

            if(DEBUG_MODE){ System.out.print("Obj func: ");
            objFunc.print(); }

            //Replacing monomials with power > 1 with z
            HashMap<SortedSet<Integer>, Double> coef = objFunc.getCoefficients();

            HashMap<SortedSet<Integer>, Integer> zReplaces = new HashMap<>();
            int zCounter = 1;
            for (SortedSet<Integer> k : coef.keySet()) {
                if (k.size() > 1) {
                    zReplaces.put(k, zCounter++);
                }
            }
            if(DEBUG_MODE) System.out.println("ZReplaces: " + Arrays.asList(zReplaces));

            //end replacing
            //Creating model file

            PrintWriter writer = new PrintWriter("model_" + suffix + ".lp" , "UTF-8");
            PrintWriter cplex_writer = new PrintWriter("cplex_model_" + suffix + ".lp" , "UTF-8");
            writer.print("min: ");
            cplex_writer.println("minimize");
            int count = 0;
            int size = coef.keySet().size();
            for (SortedSet<Integer> k : coef.keySet()) {
                count++;
                if (k.size() == 1) {
                    Integer var = k.first();
                    if (var == 0) {
                        writer.print(String.valueOf(coef.get(k)) + " + ");
                        cplex_writer.print(String.valueOf(coef.get(k)) + " + ");
                    }
                    else {
                        writer.print(String.valueOf(coef.get(k)) + " y" + String.valueOf(var) + (count == size ? ";" : " + "));
                        cplex_writer.print(String.valueOf(coef.get(k)) + " y" + String.valueOf(var) + (count == size ? "" : " + "));
                    }
                } else {
                    Integer var = zReplaces.get(k);
                    writer.print(String.valueOf(coef.get(k)) + " z" + String.valueOf(var) + (count == size ? ";" : " + "));
                    cplex_writer.print(String.valueOf(coef.get(k)) + " z" + String.valueOf(var) + (count == size ? "" : " + "));
                }
            }
            writer.println();
            cplex_writer.println();
            cplex_writer.println("subject to");
            //obj_func end

            //start constrain for y
            writer.print(String.valueOf(rows - p) + " <= ");
            for (int i = 1; i <= rows; ++i) {
                writer.print("y" + String.valueOf(i) + (i != rows ? " + " : ""));
                cplex_writer.print("y" + String.valueOf(i) + (i != rows ? " + " : ""));
            }
            writer.print(" <= " + String.valueOf(rows - p) + ";");
            cplex_writer.print(" = " + String.valueOf(rows - p));
            writer.println();
            cplex_writer.println();
            //end constrain for y

            //start constraints for y and z
            if (zReplaces.size() != 0) {
                for (SortedSet<Integer> k : zReplaces.keySet()) {
                    int y_counter = 0;
                    int y_size = k.size();
                    writer.print("0 <=");
                    for (Integer var : k) {
                        y_counter++;
                        writer.print("y" + String.valueOf(var) + (y_counter == y_size ? "" : " + "));
                        cplex_writer.print("y" + String.valueOf(var) + (y_counter == y_size ? "" : " + "));
                    }
                    writer.print(" - " + "z" + String.valueOf(zReplaces.get(k)) + " <= " + String.valueOf(y_counter - 1) + ";\n");
                    cplex_writer.print(" - " + "z" + String.valueOf(zReplaces.get(k)) + " <= " + String.valueOf(y_counter - 1) + "\n");
                }
                //end constraints for y and z

                for (SortedSet<Integer> k : zReplaces.keySet()) {
                    int y_counter = 0;
                    int y_size = k.size();
                    for (Integer var : k) {
                        y_counter++;
                        cplex_writer.print("y" + String.valueOf(var) + (y_counter == y_size ? "" : " + "));
                    }
                    cplex_writer.print(" - " + "z" + String.valueOf(zReplaces.get(k)) + " >= " + String.valueOf(0) + "\n");
                }

                //start for z
                for (SortedSet<Integer> k : zReplaces.keySet()) {
                    writer.print("z" + String.valueOf(zReplaces.get(k)) + " >= 0" + ";\n");
                }
                //end for z

                //start for int
                count = 0;
                size = zReplaces.keySet().size();
                writer.print("int ");
                cplex_writer.println("Binary");
                for (SortedSet<Integer> k : zReplaces.keySet()) {
                    count++;
                    writer.print("z" + String.valueOf(zReplaces.get(k)) + (count == size ? ";" : ","));
                    cplex_writer.print("z" + String.valueOf(zReplaces.get(k)) + (count == size ? " " : " "));
                }
                writer.println();
                //end for int
            }

            //start for binaries
            writer.print("bin ");
            for (int i = 1; i <= rows; ++i) {
                writer.print("y" + String.valueOf(i) + (i != rows ? "," : ";"));
                cplex_writer.print("y" + String.valueOf(i) + (i != rows ? " " : ""));
            }
            writer.println();
            cplex_writer.println();
            cplex_writer.println("end");
            //end for binaries

            writer.close();
            cplex_writer.close();

            coef = null;
            objFunc = null;
            zReplaces = null;

            //solve
            LpSolve lp_solver = LpSolve.readLp("model_" + suffix + ".lp", LpSolve.NORMAL, "PMP");
            System.out.println("LPSOLVE READ MODEL");
            lp_solver.solve();
            lp_solver.setOutputfile("solution_" + suffix + ".lp");
            System.out.println("LPSOLVE SOLVED MODEL");
            lp_solver.printSolution(1);
            Vector<Integer> medoids = new Vector<>();
            BufferedReader br = new BufferedReader(new FileReader("solution_" + suffix + ".lp"));
            br.readLine();
            br.readLine();
            String line;
            //read medoids
            while ((line = br.readLine()) != null) {
                if(line.startsWith("y")) {
                    String var_name = line.split(" +")[0];
                    int value = Integer.parseInt(line.split(" +")[1]);
                    if (value == 0) { //0 means it is chosen for plant location (in our case cluster center)
                        int medoid_index = (NumberFormat.getInstance().parse(var_name.substring(1))).intValue(); // var_name is f.e. "y2"
                        if (DEBUG_MODE) System.out.println("Index of medoid: " + medoid_index); // Index start from 1
                        medoids.addElement(medoid_index);
                    }
                }
            }

            if(DEBUG_MODE) System.out.println("Medoids: " + medoids);

            //filling clusters
            HashMap<Integer /*cluster*/, Vector<Integer> /*edges*/> clusters = new HashMap<>();
            HashMap<Integer /*edge*/, Integer /*cluster*/> edge_clusters = new HashMap<>();
            for (int i = 0; i < n; ++i)
            {
                Vector<Double> medoids_distances = new Vector<>();
                double min_distance = Double.MAX_VALUE;
                int cluster_index = medoids.get(0);
                for (int j = 0; j <  medoids.size(); j++) {
                    double dist = distances.get( medoids.get(j) - 1, i);
                    if(dist < min_distance) {
                        min_distance = dist;
                        cluster_index = medoids.get(j);
                    }
                }
                clusters.computeIfAbsent(cluster_index, k -> new Vector<>());
                clusters.get(cluster_index).add(i + 1);
                edge_clusters.put(i + 1, cluster_index);

            }
            if(DEBUG_MODE) System.out.println("Clusters: " + Arrays.asList(clusters));
            return clusters;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        PBPolynomial solver = new PBPolynomial();
        int n = 4;
        int p = 2;

        double[][] ar = {{7., 15., 10., 7., 10.}, {10., 17., 4., 11., 22.}, {16., 7., 6., 18., 14.}, {11., 7., 6., 12., 8.}};
        Matrix costs = new Matrix(ar);
        solver.PMedianClustering(p, costs, "comms");
        solver.formatClustersFile("comms.dat");

    }
}
