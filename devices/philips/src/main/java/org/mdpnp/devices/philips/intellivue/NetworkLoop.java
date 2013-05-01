package org.mdpnp.devices.philips.intellivue;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class NetworkLoop {
	public enum LoopState {
		/**
		 * No thread has invoked runLoop
		 */
		New,
		/**
		 * Running state, processing continues
		 */
		Resumed,
		/**
		 * The interrupt has concluded, processing may continue
		 */
		Resuming,
		/** 
		 * Processing has been successfully interrupted
		 */
		Paused,
		/**
		 * Interrupt of processing is requested
		 */
		Pausing,
		/**
		 * Termination of runLoop has been requested
		 */
		Terminating,
		/**
		 * runLoop has terminated
		 */
		Terminated
	}
	
	private final Selector select;
	private final TaskQueue taskQueue = new TaskQueue();
	private LoopState loopState = LoopState.New;
	
	private Thread myThread;
	
	public NetworkLoop() throws IOException {
		select = Selector.open();
	}
	
	public NetworkLoop(Selector select) {
		this.select = select;
	}
	
	public synchronized SelectionKey register(NetworkConnection conn, SelectableChannel channel) throws ClosedChannelException {
		long start = System.currentTimeMillis();
		if(!Thread.currentThread().equals(myThread)) {
			while(!LoopState.Paused.equals(loopState)) {
				long now = System.currentTimeMillis();
				switch(loopState) {
				case New:
					if( now >= (start + 5000L)) {
						throw new IllegalStateException("RunLoop not started after five seconds, unable to register connection");
					}
					break;
				case Terminating:
				case Terminated:
					throw new IllegalStateException("Cannot register a new connection; runLoop is " + loopState);
				case Resuming:
				case Pausing:
				case Paused:
					break;
				case Resumed:
					loopState = LoopState.Pausing;
					select.wakeup();
					break;
				}
	
				try {
					this.wait(250L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		SelectionKey key = channel.register(select, SelectionKey.OP_READ, conn);
		loopState = LoopState.Resuming;
		this.notifyAll();
		return key;
	}

	
	

	
	private int select(long time) throws IOException {
		if(time < 0L) {
			return select.selectNow();
		} else if(time == 0L) {
			return select.select();
		} else {
			return select.select(time);
		}
	}
	
	public void runLoop() {
		synchronized(this) {
			if(!LoopState.New.equals(loopState)) {
				throw new IllegalStateException("runLoop has already been called, loopState="+loopState);
			} else if(null != myThread) {
				throw new IllegalStateException("Do not invoke the runLoop from multiple threads");
			} else {
				myThread = Thread.currentThread();
				loopState = LoopState.Resumed;
				this.notifyAll();
			}
		}

		try {
			while(true) {
				synchronized(this) {
					while(!LoopState.Resumed.equals(loopState)) {
						switch(loopState) {
						case New:
						case Terminated:
							throw new IllegalStateException();
						case Terminating:
							return;
						case Pausing:
							loopState = LoopState.Paused;
							this.notifyAll();
							break;
						case Resuming:
							loopState = LoopState.Resumed;
							this.notifyAll();
							continue;
						case Paused:
						case Resumed:
						}
						try {
							this.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				
				try {
					select(taskQueue.doExpiredTasks(System.currentTimeMillis()));
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}

				java.util.Set<SelectionKey> keys = select.selectedKeys();
				for(SelectionKey sk : keys) {
					if(sk.isReadable()) {
						NetworkConnection nc = (NetworkConnection) sk.attachment();
						try {
							nc.read(sk);
						} catch (IOException e) {
							e.printStackTrace();
							sk.cancel();
							continue;
						}
					}
					if(sk.isValid()&&sk.isWritable()) {
						NetworkConnection nc = (NetworkConnection) sk.attachment();
						try {
							nc.write(sk);
						} catch (IOException e) {
							e.printStackTrace();
							sk.cancel();
							continue;
						}
					}
//					if(sk.isAcceptable()) {
//						NetworkConnection nc = (NetworkConnection) sk.attachment();
//						try {
//							nc.accept(sk);
//						} catch (IOException e) {
//							e.printStackTrace();
//							sk.cancel();
//						}
//					}
				}
			} 
		} finally {
			synchronized(this) {
				myThread = null;
				loopState = LoopState.Terminated;
				this.notifyAll();
			}
		}
	}

	public synchronized void cancelThread() {
		while(!LoopState.Resumed.equals(loopState)) {
			switch(loopState) {
			case New:
				throw new IllegalStateException("runLoop has not been started");
			case Paused:
			case Pausing:
			case Resuming:
			case Resumed:
				break;
			case Terminating:
			case Terminated:
				return;
			}
			
			try {
				this.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.loopState = LoopState.Terminating;
		select.wakeup();
	}
	
	public void cancelThreadAndWait() {
		cancelThread();
		synchronized(this) {
			while(null != myThread) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					
				}
			}
		}
	}
	public void add(TaskQueue.Task<?> task) {
		taskQueue.add(task);
		select.wakeup();
	}
	public void clearTasks() {
		taskQueue.clear();
		select.wakeup();
	}
	public void wakeup() {
		select.wakeup();
	}
}