/*
Copyright (c) 2018, Computer Science Department, Colorado State University
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

This software is provided by the copyright holders and contributors "as is" and
any express or implied warranties, including, but not limited to, the implied
warranties of merchantability and fitness for a particular purpose are
disclaimed. In no event shall the copyright holder or contributors be liable for
any direct, indirect, incidental, special, exemplary, or consequential damages
(including, but not limited to, procurement of substitute goods or services;
loss of use, data, or profits; or business interruption) however caused and on
any theory of liability, whether in contract, strict liability, or tort
(including negligence or otherwise) arising in any way out of the use of this
software, even if advised of the possibility of such damage.
*/
package galileo.integrity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import galileo.dataset.feature.Feature;
import galileo.query.Operation;
import galileo.query.Query;

/**
 * Tracks a {@link galileo.query.Query} as it traverses through a graph
 * hierarchy.
 *
 * @author malensek
 */
public class RIGQueryTracker<T> {
    public List<List<RIGPath<Feature, T>>> results = new ArrayList<>();
    private int farthestEvaluatedExpression = 0;
    private int currentLevel = 0;

    private RIGPath<Feature, T> rootPath;

    public RIGQueryTracker(RIGVertex<Feature, T> root, int numFeatures) {
        int size = numFeatures + 1;
        results = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            results.add(new ArrayList<RIGPath<Feature, T>>());
        }

        rootPath = new RIGPath<Feature, T>(root);
        List<RIGPath<Feature, T>> l = new ArrayList<>(1);
        l.add(rootPath);
        results.get(0).add(rootPath);
    }
    //.addResults(
    // Wherever this is called from may be passing incorrect path...
    public void addResults(RIGPath<Feature, T> previousPath,
            Collection<RIGVertex<Feature, T>> results) {

        for (RIGVertex<Feature, T> vertex : results) {
            RIGPath<Feature, T> path = new RIGPath<>(previousPath);
            path.add(vertex);

            /* Copy over the payload */
            if (vertex.getValues().size() > 0) {
                path.setPayload(new HashSet<>(vertex.getValues()));
            }
            this.results.get(getCurrentLevel()).add(path);
        }
    }
    
    
    public void addLastResults(RIGPath<Feature, T> previousPath, Collection<RIGVertex<Feature, T>> results) {

        for (RIGVertex<Feature, T> vertex : results) {
            RIGPath<Feature, T> path = new RIGPath<>(previousPath);
            path.add(vertex);

            /* Copy over the payload */
            if (vertex.mt != null) {
            	String payload = vertex.path+"$$"+vertex.hashValue;
            	HashSet<T> hs = new HashSet<>();
            	hs.add((T)payload);
            	
                path.setPayload(hs);
            }
            this.results.get(getCurrentLevel()).add(path);
        }
    }

    public void nextLevel() {
        ++currentLevel;
    }

    /**
     * Retrieves the current level being processed.
     */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /**
     * Retrieves the results that are currently being processed. In other words,
     * get the results from the last level in the hierarchy.
     */
    public List<RIGPath<Feature, T>> getCurrentResults() {
        return results.get(getCurrentLevel() - 1);
    }

    public void markEvaluated() {
        farthestEvaluatedExpression = getCurrentLevel();
    }
    
    
    public List<RIGPath<Feature, T>> getQueryResults() {
        List<RIGPath<Feature, T>> paths = new ArrayList<>();
        for (int i = farthestEvaluatedExpression; i < results.size(); ++i) {
            for (RIGPath<Feature, T> path : results.get(i)) {
                if (path.hasPayload()) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }


    @Override
    public String toString() {
        return "";
    }
}