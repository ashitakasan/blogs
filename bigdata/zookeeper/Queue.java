package com.wanyz.study.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class Queue extends SyncPrimitive {

	public static void main(String args[]){
		Queue q = new Queue(args[0], "/app");

		System.out.println("Input: " + args[0]);
		int i;
		Integer max = new Integer(args[1]);

		if(args[2].equals("p")){
			System.out.println("Producer");
			for(i=0; i < max; i++){
				try{
					q.produce(10 + i);
				}catch(KeeperException | InterruptedException e){
					e.printStackTrace();
				}
			}
		}
		else {
			System.out.println("Consumer");
			for(i = 0; i < max; i++){
				try {
					int r = q.consume();
					System.out.println("Item: " + r);
				}catch(KeeperException ke){
					i--;
					ke.printStackTrace();
				}catch (InterruptedException ie){
					ie.printStackTrace();
				}
			}
		}
	}

	Queue(String address, String name){
		super(address);
		this.root = name;

		if(zk != null){
			try{
				Stat s = zk.exists(root, false);
				if(s == null){
					zk.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				}
			}catch (KeeperException ke){
				System.out.println("Keeper exception when instantiating queue: " + ke.toString());
				ke.printStackTrace();
			}catch (InterruptedException ie){
				System.out.println(ie.toString());
				ie.printStackTrace();
			}
		}
	}

	/**
	 * Add an element to the queue
	 */
	boolean produce(int num) throws KeeperException, InterruptedException {
		ByteBuffer bb = ByteBuffer.allocate(4);
		byte[] value;

		bb.putInt(4);
		value = bb.array();

		// 创建为可持久化的节点，保证生产者掉线，创建的节点也不会删除
		zk.create(root + "/element", value, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
		return true;
	}

	int consume() throws KeeperException, InterruptedException {
		int retValue = -1;
		Stat stat = null;
		String path = null;

		while(true){
			synchronized (mutex){
				// 这里设置的监视器是默认的监视器，也就是 Queue 的父类，在 ZooKeeper 客户端构造函数中传入；保证在子节点变化时被通知
				List<String> list = zk.getChildren(root, true);
				if(list.size() == 0){
					System.out.println("The Queue is Empty");
					mutex.wait();
				}
				else {
					Integer min = new Integer(list.get(0).substring(7));
					for(String s : list){
						Integer temp = new Integer(s.substring(7));
						if(temp < min)
							min = temp;
					}
					path = root + "/element" + min;
					System.out.println("Temporary value: " + path);

					byte[] b = zk.getData(path, false, stat);
					zk.delete(path, 0);

					ByteBuffer bb = ByteBuffer.wrap(b);
					retValue = bb.getInt();
					return retValue;
				}
			}
		}
	}

}
