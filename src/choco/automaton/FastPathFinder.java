/**
 *  Copyright (c) 1999-2010, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package choco.automaton;

import choco.kernel.common.Constant;
import choco.kernel.common.util.iterators.DisposableIntIterator;
import choco.kernel.memory.IStateIntVector;
import choco.kernel.memory.structure.StoredIndexedBipartiteSet;
import choco.kernel.solver.ContradictionException;
import choco.kernel.solver.variables.integer.IntDomainVar;
import gnu.trove.TIntStack;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: julien
 * Mail: julien.menana{at}emn.fr
 * Date: Nov 19, 2009
 * Time: 5:50:53 PM
 */
public class FastPathFinder {

StoredDirectedMultiGraph graph;

int[] sp;
int[] lp;

int nbLayer;
int nbR;

public double[][] spfs;// = new double[graph.GNodes.spfs.length][graph.nbR+1];
public double[][] spft;// = new double[graph.GNodes.spfs.length][graph.nbR+1];
double[][] lpfs;// = new double[graph.GNodes.spfs.length][graph.nbR+1];
double[][] lpft;// = new double[graph.GNodes.spfs.length][graph.nbR+1];
boolean[] modified = new boolean[2];

int[][] prevSP;
int[][] nextSP;
int[][] prevLP;
int[][] nextLP;


public FastPathFinder(StoredDirectedMultiGraph graph)
{
	this.graph = graph;
	this.sp = new int[graph.layers.length - 1];
	this.lp = new int[graph.layers.length - 1];
	this.nbLayer = graph.layers.length - 1;
	this.nbR = this.graph.nbR - 1;
	this.tmpU = new double[nbR];
	spfs = this.graph.GNodes.spfsI;
	spft = this.graph.GNodes.spftI;
	lpfs = this.graph.GNodes.lpfsI;
	lpft = this.graph.GNodes.lpftI;
	prevSP = this.graph.GNodes.prevSPI;
	nextSP = this.graph.GNodes.nextSPI;
	prevLP = this.graph.GNodes.prevLPI;
	nextLP = this.graph.GNodes.nextLPI;


}

private final double getCost(int e, int resource, double[] u, boolean lagrange, boolean max)
{
	double cost;
	if (!lagrange) { cost = graph.GArcs.originalCost[e][resource]; } else {
		double tmp = 0.0;
		for (int k = 1; k <= nbR; k++) {
			tmp += (u[k - 1]) * graph.GArcs.originalCost[e][k];
		}
		if (max) tmp = -tmp;
		cost = graph.GArcs.originalCost[e][0] + tmp;
	}
	graph.GArcs.temporaryCost[e] = cost;
	return cost;
}

private double[] tmpU;

private final double[] simplifyLagrangian(double[] u)
{
	for (int k = 1; k <= nbR; k++) { tmpU[k - 1] = u[k - 1] - u[k - 1 + nbR]; }
	return tmpU;
}

private static final boolean isAllZero(double[] u)
{
	for (double d : u) {
		if (d != 0.0) return false;
	}
	return true;
}


public void computeLongestPath(TIntStack removed, double lb, double[] u, boolean lagrange, boolean max, int resource) throws ContradictionException
{

	boolean update;
	if (lagrange) {
		if (isAllZero(u)) {
			u = null;
			lagrange = false;
			resource = 0;
		} else {
			u = simplifyLagrangian(u);
		}
	}

	graph.GNodes.lpfs[graph.sourceIndex] = 0.0;
	graph.GNodes.lpft[graph.tinIndex] = 0.0;

	for (int i = 1; i <= nbLayer; i++) {
		update = false;

		final int[] list = graph.layers[i]._getStructure();
		final int size = graph.layers[i].size();
		// DisposableIntIterator destIter = graph.layers[i].getIterator();
		//while (destIter.hasNext()) {
		for (int w = size - 1; w >= 0; w--) {
			//int dest = destIter.next();
			int dest = list[w];
			StoredIndexedBipartiteSet bs = graph.GNodes.inArcs[dest];
			assert (!bs.isEmpty());
			final int[] inlist = bs._getStructure();
			final int insize = bs.size();
			graph.GNodes.lpfs[dest] = Double.NEGATIVE_INFINITY;
			for (int x = 0; x < insize; x++) //while (in.hasNext())
			{
				int e = inlist[x];//in.next();
				if (!graph.isInStack(e)) {
					int orig = graph.GArcs.origs[e];//e.getDestination();
					double newCost = graph.GNodes.lpfs[orig] + getCost(e, resource, u, lagrange, max);//cost[graph.GNodes.layers[orig]][graph.GArcs.values[e]];

					if (graph.GNodes.lpfs[dest] < newCost) {
						graph.GNodes.lpfs[dest] = newCost;
						graph.GNodes.prevLP[dest] = e;
						update = true;
					}
				}

			}
			//in.dispose();

		}
		//  destIter.dispose();
		if (!update) this.graph.constraint.fail();
	}
	for (int i = nbLayer - 1; i >= 0; i--) {
		update = false;
		//DisposableIntIterator origIter = graph.layers[i].getIterator();
		final int[] list = graph.layers[i]._getStructure();
		final int size = graph.layers[i].size();
		//while(origIter.hasNext()){
		for (int w = size - 1; w >= 0; w--) {
			//int orig = origIter.next();
			int orig = list[w];
			StoredIndexedBipartiteSet bs = graph.GNodes.outArcs[orig];
			assert (!bs.isEmpty());

			final int[] outlist = bs._getStructure();//getIterator();
			final int outsize = bs.size();

			graph.GNodes.lpft[orig] = Double.NEGATIVE_INFINITY;
			for (int x = 0; x < outsize; x++) //while (out.hasNext())
			{
				int e = outlist[x];//out.next();
				if (!graph.isInStack(e)) {
					int next = graph.GArcs.dests[e];
					double newCost = graph.GNodes.lpft[next] + graph.GArcs.temporaryCost[e];//cost[graph.GNodes.layers[next]][graph.GArcs.values[e]];
					if (newCost + graph.GNodes.lpfs[orig] - lb <= -Constant.MCR_DECIMAL_PREC) {
						graph.setInStack(e);
						removed.push(e);
					} else if (graph.GNodes.lpft[orig] < newCost) {
						graph.GNodes.lpft[orig] = newCost;
						graph.GNodes.nextLP[orig] = e;
						update = true;
					}
				}

			}
			//out.dispose();

		}
		//origIter.dispose();
		if (!update) this.graph.constraint.fail();
	}


}

public final double getLongestPathValue()
{
	return graph.GNodes.lpft[graph.sourceIndex];
}

public int[] getLongestPath()
{
	int i = 0;
	int current = this.graph.sourceIndex;
	do {
		int e = graph.GNodes.nextLP[current];//current.getSptt();
		sp[i++] = e;
		current = graph.GArcs.dests[e];//.getDestination();

	} while (graph.GNodes.nextLP[current] != Integer.MIN_VALUE);
	return sp;
}


public void computeShortestPath(TIntStack removed, double ub, double[] u, boolean lagrange, boolean max, int resource) throws ContradictionException
{

	graph.GNodes.spfs[graph.sourceIndex] = 0.0;
	graph.GNodes.spft[graph.tinIndex] = 0.0;
	boolean update;
	if (lagrange) {
		if (isAllZero(u)) {
			u = null;
			lagrange = false;
			resource = 0;
		} else {
			u = simplifyLagrangian(u);
		}
	}


	for (int i = 1; i <= nbLayer; i++) {
		update = false;

		int[] list = graph.layers[i]._getStructure();
		int size = graph.layers[i].size();
		// DisposableIntIterator destIter = graph.layers[i].getIterator();
		//while (destIter.hasNext()) {
		for (int w = size - 1; w >= 0; w--) {
			//int dest = destIter.next();
			int dest = list[w];
			graph.GNodes.spfs[dest] = Double.POSITIVE_INFINITY;
			StoredIndexedBipartiteSet bs = graph.GNodes.inArcs[dest];
			assert (!bs.isEmpty());
			final int[] inlist = bs._getStructure();
			final int insize = bs.size();

			for (int x = 0; x < insize; x++) //while (in.hasNext())
			{
				int e = inlist[x];//in.next();
				if (!graph.isInStack(e)) {
					int orig = graph.GArcs.origs[e];//.getDestination();
					double newCost = graph.GNodes.spfs[orig] + getCost(e, resource, u, lagrange, max);//cost[i][graph.GArcs.values[e]];
					if (graph.GNodes.spfs[dest] > newCost) {
						graph.GNodes.spfs[dest] = newCost;
						graph.GNodes.prevSP[dest] = e;
						update = true;

					}
				}
			}
			// in.dispose();
		}
		//  destIter.dispose();
		if (!update) this.graph.constraint.fail();
	}
	for (int i = nbLayer - 1; i >= 0; i--) {
		update = false;
		//DisposableIntIterator origIter = graph.layers[i].getIterator();
		int[] list = graph.layers[i]._getStructure();
		int size = graph.layers[i].size();
		//while(origIter.hasNext()){
		for (int w = size - 1; w >= 0; w--) {
			//int orig = origIter.next();
			int orig = list[w];
			graph.GNodes.spft[orig] = Double.POSITIVE_INFINITY;
			StoredIndexedBipartiteSet bs = graph.GNodes.outArcs[orig];
			assert (!bs.isEmpty());
			final int[] outlist = bs._getStructure();//getIterator();
			final int outsize = bs.size();
			for (int x = 0; x < outsize; x++) //while (out.hasNext())
			{
				int e = outlist[x];//out.next();
				if (!graph.isInStack(e)) {
					int dest = graph.GArcs.dests[e];//e.getOrigin()  ;
					double newCost = graph.GNodes.spft[dest] + graph.GArcs.temporaryCost[e];
					if (newCost + graph.GNodes.spfs[orig] - ub >= Constant.MCR_DECIMAL_PREC) {
						graph.setInStack(e);
						removed.push(e);
					} else if (graph.GNodes.spft[orig] > newCost) {
						graph.GNodes.spft[orig] = newCost;
						graph.GNodes.nextSP[orig] = e;
						update = true;
					}
				}
			}
			//  out.dispose();


		}
		//origIter.dispose();
		if (!update) this.graph.constraint.fail();
	}

}

public final double getShortestPathValue()
{
	return graph.GNodes.spft[graph.sourceIndex];
}

public int[] getShortestPath()
{
	int i = 0;
	int current = this.graph.sourceIndex;
	do {
		int e = graph.GNodes.nextSP[current];//current.getSptt();
		sp[i++] = e;
		current = graph.GArcs.dests[e];//.getDestination();

	} while (graph.GNodes.nextSP[current] != Integer.MIN_VALUE);
	return sp;
}


public void computeShortestAndLongestPath(IStateIntVector removed, int lb, int ub, double[] u, boolean lagrange, boolean max, int resource) throws ContradictionException
{

	graph.GNodes.spfs[graph.sourceIndex] = 0.0;
	graph.GNodes.spft[graph.tinIndex] = 0.0;
	graph.GNodes.lpfs[graph.sourceIndex] = 0.0;
	graph.GNodes.lpft[graph.tinIndex] = 0.0;
	boolean update;
	if (lagrange) {
		if (isAllZero(u)) {
			u = null;
			lagrange = false;
			resource = 0;
		} else {
			u = simplifyLagrangian(u);
		}
	}

	for (int i = 1; i <= nbLayer; i++) {
		update = false;
		DisposableIntIterator destIter = graph.layers[i].getIterator();
		while (destIter.hasNext()) {
			int dest = destIter.next();
			graph.GNodes.spfs[dest] = Double.POSITIVE_INFINITY;
			graph.GNodes.lpfs[dest] = Double.NEGATIVE_INFINITY;
			StoredIndexedBipartiteSet bs = graph.GNodes.inArcs[dest];
			assert (!bs.isEmpty());
			DisposableIntIterator in = bs.getIterator();
			while (in.hasNext()) {
				int e = in.next();
				if (!graph.isInStack(e)) {
					int orig = graph.GArcs.origs[e];//.getDestination();
					double cost = getCost(e, resource, u, lagrange, max);
					double newCost = graph.GNodes.spfs[orig] + cost;//cost[i][graph.GArcs.values[e]];
					if (graph.GNodes.spfs[dest] > newCost) {
						graph.GNodes.spfs[dest] = newCost;
						graph.GNodes.prevSP[dest] = e;
						update = true;

					}
					double newCost2 = graph.GNodes.lpfs[orig] + cost;//cost[graph.GNodes.layers[n]][graph.GArcs.values[e]];

					if (graph.GNodes.lpfs[dest] < newCost2) {
						graph.GNodes.lpfs[dest] = newCost2;
						graph.GNodes.prevLP[dest] = e;
						update = true;
					}
				}
			}
			in.dispose();

		}
		destIter.dispose();
		if (!update) this.graph.constraint.fail();
	}
	for (int i = nbLayer - 1; i >= 0; i--) {
		update = false;
		DisposableIntIterator origIter = graph.layers[i].getIterator();
		while (origIter.hasNext()) {
			int orig = origIter.next();
			graph.GNodes.spft[orig] = Double.POSITIVE_INFINITY;
			graph.GNodes.lpft[orig] = Double.NEGATIVE_INFINITY;
			StoredIndexedBipartiteSet bs = graph.GNodes.outArcs[orig];
			assert (!bs.isEmpty());
			DisposableIntIterator out = bs.getIterator();//getInEdgeIterator(n);
			while (out.hasNext()) {
				int e = out.next();
				if (!graph.isInStack(e)) {
					int dest = graph.GArcs.dests[e];//e.getOrigin()  ;
					double cost = graph.GArcs.temporaryCost[e];

					double newCost = graph.GNodes.spft[dest] + cost;//cost[graph.GNodes.layers[next]][graph.GArcs.values[e]];
					if (newCost + graph.GNodes.spfs[orig] - ub >= Constant.MCR_DECIMAL_PREC) {
						graph.getInStack().set(e);
						removed.add(e);
					} else if (graph.GNodes.spft[orig] > newCost) {
						graph.GNodes.spft[orig] = newCost;
						graph.GNodes.nextSP[orig] = e;
						update = true;
					}

					double newCost2 = graph.GNodes.lpft[dest] + cost;//cost[graph.GNodes.layers[next]][graph.GArcs.values[e]];
					if (newCost2 + graph.GNodes.lpfs[orig] - lb <= -Constant.MCR_DECIMAL_PREC) {
						graph.setInStack(e);
						removed.add(e);
					} else if (graph.GNodes.lpft[orig] < newCost2) {
						graph.GNodes.lpft[orig] = newCost2;
						graph.GNodes.nextLP[orig] = e;
						update = true;
					}


				}
			}
			out.dispose();

		}
		origIter.dispose();
		if (!update) this.graph.constraint.fail();
	}


}

public boolean[] computeShortestAndLongestPath(TIntStack removed, IntDomainVar[] z) throws ContradictionException
{

	int nbr = z.length;

	for (int i = 0; i < nbr; i++) {
		spfs[graph.sourceIndex][i] = 0.0;
		spft[graph.tinIndex][i] = 0.0;
		lpfs[graph.sourceIndex][i] = 0.0;
		lpft[graph.tinIndex][i] = 0.0;

	}
	boolean update;

	for (int i = 1; i <= nbLayer; i++) {
		update = false;
		int[] list = graph.layers[i]._getStructure();
		int size = graph.layers[i].size();
		// DisposableIntIterator destIter = graph.layers[i].getIterator();
		//while (destIter.hasNext()) {
		for (int w = size - 1; w >= 0; w--) {
			//int dest = destIter.next();
			int dest = list[w];
			Arrays.fill(spfs[dest], Double.POSITIVE_INFINITY);
			Arrays.fill(lpfs[dest], Double.NEGATIVE_INFINITY);

			StoredIndexedBipartiteSet bs = graph.GNodes.inArcs[dest];
			assert (!bs.isEmpty());
			final int[] inlist = bs._getStructure();
			final int insize = bs.size();

			for (int x = 0; x < insize; x++) //while (in.hasNext())
			{
				int e = inlist[x];//in.next();
				if (!graph.isInStack(e)) {
					int orig = graph.GArcs.origs[e];//.getDestination();
					double[] cost = graph.GArcs.originalCost[e];
//                        double[] newCost = addArray(spfs[orig],cost);//cost[i][graph.GArcs.values[e]];
					for (int d = 0; d < nbr; d++) {
						if (spfs[dest][d] > cost[d] + spfs[orig][d]) {
							spfs[dest][d] = cost[d] + spfs[orig][d];
							prevSP[dest][d] = e;
							update = true;
						}
						if (lpfs[dest][d] < lpfs[orig][d] + cost[d]) {
							lpfs[dest][d] = lpfs[orig][d] + cost[d];
							prevLP[dest][d] = e;
							update = true;
						}
					}
				}
			}
			//  in.dispose();

		}
		//  destIter.dispose();
		if (!update) this.graph.constraint.fail();
	}
	for (int i = nbLayer - 1; i >= 0; i--) {
		update = false;
		//DisposableIntIterator origIter = graph.layers[i].getIterator();
		int[] list = graph.layers[i]._getStructure();
		int size = graph.layers[i].size();
		//while(origIter.hasNext()){
		for (int w = size - 1; w >= 0; w--) {
			//int orig = origIter.next();
			int orig = list[w];
			Arrays.fill(spft[orig], Double.POSITIVE_INFINITY);
			Arrays.fill(lpft[orig], Double.NEGATIVE_INFINITY);
			StoredIndexedBipartiteSet bs = graph.GNodes.outArcs[orig];
			assert (!bs.isEmpty());
			final int[] outlist = bs._getStructure();//getIterator();
			final int outsize = bs.size();
			for (int x = 0; x < outsize; x++) //while (out.hasNext())
			{
				int e = outlist[x];//out.next();
				if (!graph.isInStack(e)) {
					int dest = graph.GArcs.dests[e];//e.getOrigin()  ;
					double[] cost = graph.GArcs.originalCost[e];

					for (int d = 0; d < nbr; d++) {
						if (spft[dest][d] + cost[d] + spfs[orig][d] - z[d].getSup() >= Constant.MCR_DECIMAL_PREC) {
							graph.getInStack().set(e);
							removed.push(e);
							break;
						} else if (spft[orig][d] > spft[dest][d] + cost[d]) {
							spft[orig][d] = spft[dest][d] + cost[d];
							nextSP[orig][d] = e;
							update = true;
						}

						if (lpft[dest][d] + cost[d] + lpfs[orig][d] - z[d].getInf() <= -Constant.MCR_DECIMAL_PREC) {
							graph.setInStack(e);
							removed.push(e);
							break;
						} else if (lpft[orig][d] < lpft[dest][d] + cost[d]) {
							lpft[orig][d] = lpft[dest][d] + cost[d];
							nextLP[orig][d] = e;
							update = true;
						}

					}

				}
			}
			//  out.dispose();

		}
		//  origIter.dispose();
		if (!update) this.graph.constraint.fail();
	}

	modified[0] = z[0].updateInf((int) Math.ceil(spft[graph.sourceIndex][0]), this.graph.constraint, false);
	modified[1] = z[0].updateSup((int) Math.floor(lpft[graph.sourceIndex][0]), this.graph.constraint, false);


	for (int i = 1; i < nbr; i++) {
		z[i].updateInf((int) Math.ceil(spft[graph.sourceIndex][i]), this.graph.constraint, false);
		z[i].updateSup((int) Math.floor(lpft[graph.sourceIndex][i]), this.graph.constraint, false);
	}

	return modified;
}

private static double[] addArray(double[] spf, double[] cost)
{
	double[] out = new double[spf.length];
	for (int i = 0; i < out.length; i++) {
		out[i] = spf[i] + cost[i];
	}
	return out;
}


/*  public int getMaxNbOccurence(ArrayList<int[]> subpattern, Automaton pi)
{
long time = System.currentTimeMillis();
int n = this.graph.GNodes.states.length;
int sum = 0;
int[][] mat = new int[subpattern.size()][n];

for (int i = 0 ; i < this.graph.offsets.length ; i++)
{
for (int idx = subpattern.size()-1 ; idx >= 0 ; idx--)
{
    int[] elem = subpattern.get(idx);

    for (int orig : graph.layers[i]) {
        StoredIndexedBipartiteSet bs = graph.GNodes.outArcs[orig];
        if (!bs.isEmpty())
        {
            DisposableIntIterator out = bs.getIterator();

            while (out.hasNext())
            {
                int e = out.next();
               int val = graph.GArcs.values[e];
                if (contains(val,elem))
                {
                    int dest = graph.GArcs.dests[e];
                    int add;
                    if (idx == 0)
                        add = 1+ mat[subpattern.size()-1][orig];
                    else
                    {
                        add = mat[idx-1][orig];
                        mat[idx-1][orig] = 0;
                    }
                    mat[idx][dest] = Math.max(mat[idx][dest],add);
                }

            }
        }
    }
}
}
for (int idx : this.graph.layers[this.graph.offsets.length])
{
    int tmp = mat[subpattern.size()-1][idx];
    sum = Math.max(tmp,sum);
    //mat[subpattern.size()-1][idx] = 0;
}
time = System.currentTimeMillis()-time;
System.out.println("TEMPS POUR NB OCCURENCE : "+time+"ms");

return sum;

}


public int getMinNbOccurence(ArrayList<int[]> subpattern, Automaton pi)
{
long time = System.currentTimeMillis();
int n = this.graph.GNodes.states.length;
int sum = Integer.MAX_VALUE;
int[][] mat = new int[subpattern.size()][n];

for (int i = 0 ; i < this.graph.offsets.length ; i++)
{
for (int idx = subpattern.size()-1 ; idx >= 0 ; idx--)
{
    int[] elem = subpattern.get(idx);

    for (int orig : graph.layers[i]) {
        StoredIndexedBipartiteSet bs = graph.GNodes.outArcs[orig];
        if (!bs.isEmpty())
        {
            DisposableIntIterator out = bs.getIterator();

            while (out.hasNext())
            {
                int e = out.next();
               int val = graph.GArcs.values[e];
                if (contains(val,elem))
                {
                    int dest = graph.GArcs.dests[e];
                    int add;
                    if (idx == 0)
                        add = 1+ mat[subpattern.size()-1][orig];
                    else
                    {
                        add = mat[idx-1][orig];
                        mat[idx-1][orig] = 0;
                    }
                    mat[idx][dest] = Math.min(mat[idx][dest],add);
                }

            }
        }
    }
}
}
for (int idx : this.graph.layers[this.graph.offsets.length])
{
    int tmp = mat[subpattern.size()-1][idx];
    sum = Math.min(tmp,sum);
    //mat[subpattern.size()-1][idx] = 0;
}
time = System.currentTimeMillis()-time;
System.out.println("TEMPS POUR NB OCCURENCE : "+time+"ms");

return sum;

}


private static boolean contains(int i, int[] tab)
{
for (int e : tab)
{
if (i == e)
    return true;

}
return false;
}
*/

}