package com.wanyz.study.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;

import java.util.Arrays;

public class DataMonitor implements Watcher, AsyncCallback.StatCallback{

	ZooKeeper zk;

	String znode;

	Watcher chainedWatcher;

	boolean dead;

	DataMonitorListener listener;

	byte prevData[];

	public DataMonitor(ZooKeeper zk, String znode, Watcher chainedWatcher, DataMonitorListener listener){
		this.zk = zk;
		this.znode = znode;
		this.chainedWatcher = chainedWatcher;
		this.listener = listener;

		// 通过检查节点是否存在来决定是否启动程序，完全是事件驱动
		zk.exists(znode, true, this, null);
	}

	/**
	 * 调用 zk.exists() 成功后，调用该回调函数
	 */
	@Override
	public void processResult(int rc, String path, Object ctx, Stat stat){
		boolean exists;
		switch(rc){                         // 根据 exists() 的返回代码，判断 znode 节点是否存在
			case Code.Ok:
				exists = true;
				break;
			case Code.NoNode:
				exists = false;
				break;
			case Code.SessionExpired:
			case Code.NoAuth:
				dead = true;
				listener.closing(rc);
				return;
			default:
				zk.exists(znode, true, this, null);
				return;
		}

		// 检测节点数据的逻辑必须放在这里，必须在 exists 确定节点存在后才能检测其数据
		byte[] b = null;
		if(exists){
			try{
				b = zk.getData(znode, false, null);
			}catch (KeeperException ke){
				ke.printStackTrace();
			}catch (InterruptedException ie){
				return;
			}
		}
		// znode 数据更改
		if((b == null && b != prevData) || (b != null && !Arrays.equals(b, prevData))){
			listener.changed(b);
			prevData = b;
		}
		else {
			System.out.println("catch znode event, but znode data does not changed");
		}
	}

	@Override
	public void process(WatchedEvent event){
		String path = event.getPath();
		if(event.getType() == Event.EventType.None){
			switch(event.getState()){
				case SyncConnected:
					break;
				case Expired:
					dead = true;
					listener.closing(Code.SessionExpired);
					break;
			}
		}
		else{
			if(path != null && path.equals(znode)){
				zk.exists(znode, true, this, null);
			}
		}
		if(chainedWatcher != null)
			chainedWatcher.process(event);
	}

	public interface DataMonitorListener {

		/**
		 * 节点的存在状态已更改
		 * @param data
		 */
		void changed(byte data[]);

		/**
		 * @param c ZooKeeper 返回代码
		 */
		void closing(int c);

	}

}

