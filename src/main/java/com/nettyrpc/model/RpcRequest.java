package com.nettyrpc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * RPC Request
 * 
 * @author huangyong
 */
@Setter
@Getter
public class RpcRequest {

	private String requestId;

	private String className;

	private String methodName;

	private Class<?>[] parameterTypes;

	private Object[] parameters;

}