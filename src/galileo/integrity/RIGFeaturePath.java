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

import java.util.List;
import java.util.Set;

import galileo.dataset.feature.Feature;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Query;

/**
 * Contains a graph {@link Path} composed of Features.
 *
 * @author malensek
 */
public class RIGFeaturePath<V> extends RIGPath<Feature, V> {

    public RIGFeaturePath(Feature... features) {
        for (Feature f : features) {
            this.add(new RIGVertex<Feature, V>(f));
        }
    }
    //new FeaturePath<
    public RIGFeaturePath(V payload, long hashValue, Feature... features) {
        this(features);
        this.addPayload(payload);
        this.merkleHash = hashValue;
    }

    public RIGFeaturePath(Set<V> payload, Feature... features) {
        this(features);
        this.setPayload(payload);
    }

    
}
