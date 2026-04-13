package com.example.wordtrigger;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpService {
    private DatagramSocket socket;
    private boolean isRunning = false;
    private final OnDataReceived listener;
    private InetAddress lastSenderAddress;
    private int lastSenderPort;

    public interface OnDataReceived {
        void onAudio(byte[] data, int len);
    }

    public UdpService(OnDataReceived listener) {
        this.listener = listener;
    }

    public void startListening(int port) {
        isRunning = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket(50005);
                socket.setBroadcast(true);
                byte[] buffer = new byte[2048];
                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    lastSenderAddress = packet.getAddress();
                    lastSenderPort = packet.getPort();

                    listener.onAudio(packet.getData(), packet.getLength());
                    Log.d("UDP_DEBUG", "Получен пакет, длина: " + packet.getLength());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void sendVibrate() {
        if (socket == null || lastSenderAddress == null) return;
        new Thread(() -> {
            try {
                byte[] data = "V".getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, lastSenderAddress, lastSenderPort);
                socket.send(packet);
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void stop() {
        isRunning = false;
        if (socket != null) socket.close();
    }
}