// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api.response;

import java.util.List;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class NetworkACLResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID) @Param(description="the ID of the ACL")
    private String id;

    @SerializedName(ApiConstants.PROTOCOL) @Param(description="the protocol of the ACL")
    private String protocol;

    @SerializedName(ApiConstants.START_PORT) @Param(description="the starting port of ACL's port range")
    private String startPort;

    @SerializedName(ApiConstants.END_PORT)  @Param(description = "the ending port of ACL's port range")
    private String endPort;

    @SerializedName(ApiConstants.TRAFFIC_TYPE) @Param(description="the traffic type for the ACL")
    private String trafficType;

    @SerializedName(ApiConstants.STATE) @Param(description="the state of the rule")
    private String state;

    @SerializedName(ApiConstants.CIDR_LIST) @Param(description="the cidr list to forward traffic from")
    private String cidrList;

    @SerializedName(ApiConstants.ICMP_TYPE) @Param(description= "type of the icmp message being sent")
    private Integer icmpType;

    @SerializedName(ApiConstants.ICMP_CODE) @Param(description = "error code for this icmp message")
    private Integer icmpCode;

    @SerializedName(ApiConstants.TAGS)  @Param(description="the list of resource tags associated with the network ACLs",
            responseObject = ResourceTagResponse.class)
    private List<ResourceTagResponse> tags;

    public void setId(String id) {
        this.id = id;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setStartPort(String startPort) {
        this.startPort = startPort;
    }

    public void setEndPort(String endPort) {
        this.endPort = endPort;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setCidrList(String cidrList) {
        this.cidrList = cidrList;
    }

    public void setIcmpType(Integer icmpType) {
        this.icmpType = icmpType;
    }

    public void setIcmpCode(Integer icmpCode) {
        this.icmpCode = icmpCode;
    }

    public void setTrafficType(String trafficType) {
        this.trafficType = trafficType;
    }

    public void setTags(List<ResourceTagResponse> tags) {
        this.tags = tags;
    }
}
