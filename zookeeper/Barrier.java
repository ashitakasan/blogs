package com.wanyz.study.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

public class Barrier extends SyncPrimitive{

	int size;
	String path;

	Barrier(String address, String root, int size){
		super(address);
		this.root = root;
		this.size = size;

		if(zk != null){
			try{
				Stat s = zk.exists(root, false);                // 创建 Barrier 根节点
				if(s == null){
					zk.create(root, (size + "").getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				}
			}catch(KeeperException ke){
				System.out.println("Keeper exception when instantiating barrier: " + ke.toString());
				ke.printStackTrace();
			}catch (InterruptedException ie){
				System.out.println(ie.toString());
				ie.printStackTrace();
			}
		}

		try{
			path = root + "/" + InetAddress.getLocalHost().getCanonicalHostName();
		}catch(UnknownHostException ue){
			System.out.println(ue.toString());
			ue.printStackTrace();
		}
	}

	boolean enter() throws KeeperException, InterruptedException {
		// 必须创建临时节点，避免因为网络原因，导致到达屏障点的进程数大于屏障限制，节点名单调增加使得单个客户端可以创建多个进程
		path = zk.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
		while(true){
			synchronized(mutex){
				// 这里设置的监视器是默认的监视器，也就是 Barrier 的父类，在 ZooKeeper 客户端构造函数中传入；保证在子节点变化时被通知
				List<String> list = zk.getChildren(root, true);
				if(list.size() < size)
					mutex.wait();
				else
					return true;
			}
		}
	}

	boolean leave() throws KeeperException, InterruptedException {
		zk.delete(path, 0);
		while(true){
			synchronized (mutex){
				// 这里设置的监视器是默认的监视器，也就是 Barrier 的父类，在 ZooKeeper 客户端构造函数中传入
				List<String> list = zk.getChildren(root, true);
				if(list.size() > 0)
					wait();
				else {
					zk.delete(root, 0);
					return true;
				}
			}
		}
	}

	public static void main(String[] args){
		Barrier b = new Barrier(args[0], "/b1", new Integer(args[1]));
		try{
			boolean flag = b.enter();
			System.out.println("Entered barrier: " + args[1]);
			if(!flag)
				System.out.println("Error when entering the barrier");
		}catch(KeeperException | InterruptedException e){
			e.printStackTrace();
		}

		Random random = new Random();
		int r = random.nextInt(10);

		for(int i=0; i < r; i++){
			try{
				Thread.sleep(100);
			}catch (InterruptedException ie){
				ie.printStackTrace();
			}
		}
		try{
			b.leave();
		}catch(KeeperException | InterruptedException e){
			e.printStackTrace();
		}
		System.out.println("Left barrier");
	}

}
