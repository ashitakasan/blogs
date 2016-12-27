package com.wanyz.study.zookeeper;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;

public class SyncPrimitive implements Watcher{

	static ZooKeeper zk = null;
	static Integer mutex;

	String root;

	SyncPrimitive(String address){
		if(zk == null){
			try{
				System.out.println("Starting ZK...");
				zk = new ZooKeeper(address, 300000, this);
				mutex = new Integer(-1);
				System.out.println("Finished starting ZK: " + zk);
			}catch(IOException e){
				e.printStackTrace();
				zk = null;
			}
		}
	}

	@Override
	public void process(WatchedEvent event){
		synchronized(mutex){
			System.out.println("Process: " + event.getType());
			mutex.notifyAll();
		}
	}

}
