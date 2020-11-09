package com.nettyrpc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * RPC Response
 * @author huangyong
 */
@Setter
@Getter
public class RpcResponse {
	
    private String requestId;
    
    private String error;
    
    private Object result;

    public boolean isError() {
        return error != null;
    }

}
