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
package galileo.dataset.feature;

import java.io.IOException;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

/**
 * Represents an interval consisting of two floating point numbers.
 *
 * @author malensek
 */
public class FloatIntervalFeatureData
extends IntervalFeatureData<Float> implements ByteSerializable {

    public FloatIntervalFeatureData(float data1, float data2) {
        this.data = data1;
        this.data2 = data2;
        this.type = FeatureType.INTERVAL_FLOAT;
    }

    @Deserialize
    public FloatIntervalFeatureData(SerializationInputStream in)
    throws IOException {
        this.data = in.readFloat();
        this.data2 = in.readFloat();
        this.type = FeatureType.INTERVAL_FLOAT;
    }

    @Override
    public void serialize(SerializationOutputStream out)
    throws IOException {
        out.writeFloat(data);
        out.writeFloat(data2);
    }
}
