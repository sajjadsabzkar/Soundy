package com.example.soundseeder;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class User {

    private InetAddress address;
    private InputStream inputStream;
    private String ipAddress;
    private OutputStream outputStream;
    private int port;
    private PrintWriter printWriter;
    private Socket socket;
    private String userName;

    public User() {

    }

    public User(Socket socket) {
        this.socket = socket;
        try {
            this.printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            this.outputStream = socket.getOutputStream();
            this.inputStream = socket.getInputStream();
            this.ipAddress = socket.getInetAddress().getHostAddress();
            this.port = socket.getPort();
            this.address = socket.getInetAddress();
        } catch (IOException e) {
            Logger.error(e);
        }
    }
    public User(User other) {
        this.socket = other.socket;
        this.printWriter = other.printWriter;
        this.outputStream = other.outputStream;
        this.inputStream = other.inputStream;
        this.userName = other.userName;
        this.ipAddress = other.ipAddress;
        this.port = other.port;
        this.address = other.address;
    }
    public InetAddress getAddress() {
        return this.address;
    }

    public InputStream getInputStream() {
        return this.inputStream;
    }

    public int getPort() {
        return this.port;
    }

    public OutputStream getOutputStream() {
        return this.outputStream;
    }

    public PrintWriter getPrintWriter() {
        return this.printWriter;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setPrintWriter(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int hashCode() {
        return this.ipAddress.hashCode() + Integer.valueOf(this.port).hashCode();
    }

    @NonNull
    public String toString() {
        return this.userName + "\n" + this.ipAddress + ":" + this.port;
    }



}
