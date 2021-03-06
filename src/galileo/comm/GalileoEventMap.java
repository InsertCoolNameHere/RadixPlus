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
package galileo.comm;

import galileo.event.EventMap;

public class GalileoEventMap extends EventMap {
    public GalileoEventMap() {
        addMapping(10, DebugEvent.class);

        addMapping(100, StorageEvent.class);
        addMapping(101, StorageRequest.class);
        addMapping(102, NonBlockStorageRequest.class);
        addMapping(103, NonBlockStorageEvent.class);
        addMapping(104, IRODSRequest.class);
        
        
        addMapping(105, IRODSReadyCheckRequest.class);
        addMapping(106, IRODSReadyCheckResponse.class);
        
        addMapping(200, QueryEvent.class);
        addMapping(201, QueryRequest.class);
        addMapping(202, QueryPreamble.class);
        addMapping(203, QueryResponse.class);
        
        addMapping(301, MetadataRequest.class);
        addMapping(302, MetadataResponse.class);
        addMapping(303, MetadataEvent.class);
        
        addMapping(401, BlockRequest.class);
        addMapping(402, BlockResponse.class);
        
        addMapping(501, FilesystemRequest.class);
        addMapping(502, TemporalFilesystemEvent.class);
        addMapping(503, TemporalFilesystemRequest.class);
        addMapping(504, FilesystemEvent.class);
        
        addMapping(601, BlockQueryRequest.class);
        addMapping(602, BlockQueryResponse.class);
        addMapping(603, RigUpdateRequest.class);
        
        addMapping(701, QueueRequest.class);
        addMapping(702, QueueResponse.class);
        
    }
}
