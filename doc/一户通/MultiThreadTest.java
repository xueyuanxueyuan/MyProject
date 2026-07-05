package com.zayk.svs.test;

import com.zayk.svs.api.ZaSVSApi;
import com.zayk.svs.api.debug.Log;
import com.zayk.svs.util.encoders.Hex;

//应用程序运行只需要调用一次InitServiceConnect
//并且应用程序退出时务必调用CloseServiceConnect

//严禁多线程分别InitServiceConnect
public class MultiThreadTest {

	public static void main(String[] args) {
		
		int thrdNum = 8;
		int loopNum = 10;
		
		Thread t[] = new Thread[128];
	
		ZaSVSApi.InitServiceConnect(null);
		
		for(int i = 0; i < thrdNum; ++i) {
			t[i] = new Thread(() -> {
				
				for(int j = 0; j < loopNum; ++j) {
					try {
						byte[] outData = ZaSVSApi.Random(32);
						if (outData == null) {
							System.out.println("---------->Random Err");
						} else {
							Log.writeTransData(outData, "Random_resp_data.txt");
							System.out.println("==========>Random OK:(length=32)" + Hex.toHexString(outData));
						}
					} catch (Exception e) {
						System.out.println("---------->ExportCert system Err  msg:" + e.getMessage());
					}
				}
				
			});
			t[i].start();
		}
		
		for (int i = 0; i < thrdNum; i++) {
			try {
				t[i].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		ZaSVSApi.CloseServiceConnect();
		System.out.println("Test end.");

	}

}
