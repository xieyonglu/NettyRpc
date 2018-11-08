package com.nettyrpc.client;

import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RPCFuture for async RPC call
 * Created by luxiaoxun on 2016-03-15.
 */
public class RPCFuture implements Future<Object> {
    private static final Logger logger = LoggerFactory.getLogger(RPCFuture.class);

    private Sync sync;
    private RpcRequest request;
    private RpcResponse response;
    private long startTime;
    private long responseTimeThreshold = 5000;

    private List<AsyncRPCCallback> pendingCallbacks = new ArrayList<AsyncRPCCallback>();
    private ReentrantLock lock = new ReentrantLock();

    public RPCFuture(RpcRequest request) {
        this.sync = new Sync();
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
    	/**
    	 * 以独占模式获取对象，忽略中断。
    	 * 独占式获取锁，如果当前线程成功获取锁，那么方法就返回，否则会将当前线程放入同步队列等待。该方法会调用重写的 tryAcquire(int arg)方法判断是否可以获得锁
    	 */
        sync.acquire(-1);
        if (this.response != null) {
            return this.response.getResult();
        } else {
            return null;
        }
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    	/**
    	 * 试图以独占模式获取对象，如果被中断则中止，如果到了给定超时时间，则会失败。
    	 * 在 acquireInterruptibly(int) 基础上添加了超时控制，同时支持中断和超时，当在指定时间内没有获得锁时，会返回 false，获取到了返回 true
    	 */
        boolean success = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (success) {
            if (this.response != null) {
                return this.response.getResult();
            } else {
                return null;
            }
        } else {
            throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                    + ". Request class name: " + this.request.getClassName()
                    + ". Request method: " + this.request.getMethodName());
        }
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    public void done(RpcResponse reponse) {
        this.response = reponse;
        /**
         * 独占式释放锁，该方法会在释放锁后，将同步队列中第一个等待节点唤醒
         */
        sync.release(1);
        invokeCallbacks();
        // Threshold
        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime > this.responseTimeThreshold) {
            logger.warn("Service response time is too slow. Request id = " + reponse.getRequestId() + ". Response Time = " + responseTime + "ms");
        }
    }

    private void invokeCallbacks() {
        lock.lock();
        try {
            for (final AsyncRPCCallback callback : pendingCallbacks) {
                runCallback(callback);
            }
        } finally {
            lock.unlock();
        }
    }

    public RPCFuture addCallback(AsyncRPCCallback callback) {
        lock.lock();
        try {
            if (isDone()) {
                runCallback(callback);
            } else {
                this.pendingCallbacks.add(callback);
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    private void runCallback(final AsyncRPCCallback callback) {
        final RpcResponse res = this.response;
        RpcClient.submit(new Runnable() {
            @Override
            public void run() {
                if (!res.isError()) {
                    callback.success(res.getResult());
                } else {
                    callback.fail(new RuntimeException("Response error", new Throwable(res.getError())));
                }
            }
        });
    }

    static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 1L;

        //future status
        private final int done = 1;
        private final int pending = 0;

        /**
         * 尝试获取
         */
        @Override
        protected boolean tryAcquire(int arg) {
            return getState() == done;
        }

        /**
         * 尝试释放
         */
        @Override
        protected boolean tryRelease(int arg) {
            if (getState() == pending) {
                if (compareAndSetState(pending, done)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }

        public boolean isDone() {
            getState();
            return getState() == done;
        }
    }
}
