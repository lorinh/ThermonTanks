package com.example.lorin.thermontanks.Multiplayer;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;

import com.example.lorin.thermontanks.Camera;
import com.example.lorin.thermontanks.Tank.BulletContainer;
import com.example.lorin.thermontanks.Tank.MultiplayerTank;
import com.example.lorin.thermontanks.Tank.Tank;
import com.example.lorin.thermontanks.Tank.TankApperance;
import com.example.lorin.thermontanks.Vector2;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import android.util.Base64;

/**
 * Created by lorinhersh on 9/20/17.
 * The multiplayer aspect of the game
 * Creates "MultiplayerTanks" that are rendered on screen
 */

public class Multiplayer {

    private Context context;
    private Camera camera;
    private Tank localTank;
    private BulletContainer localBulletContainer;

    private MultiplayerTank[] tanks = new MultiplayerTank[5]; // Limited to 5 players for now
    private Packet lastPacket;

    //Delay between spamming server
    private boolean responded = true;
    private int requestDelay = 0;
    private final int REQUESTWAIT = 6;
    private String damageTarget = "";

    private final static String SERVERADDRESS = "Lund.ad.mvnu.edu";
    private final static int PORT = 25565;

    public Multiplayer(Context context, Camera camera, Tank localTank, BulletContainer bulletContainer) {
        this.context = context;
        this.camera = camera;
        this.localTank = localTank;
        this.localBulletContainer = bulletContainer;
    }

    //Main loop of Multiplayer
    public void run() {
        if (responded) {
            requestDelay = (requestDelay + 1) % REQUESTWAIT;
            if (requestDelay == 0) {
                responded = false; //Don't wait for last response (When = true)

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        sendData();
                        lastPacket = getData();
                        handleData();
                        responded = true;
                    }
                });
                thread.start();
            }
        }
    }

    //Send data to the server
    public void sendData() {
        try {
            //Log.e("Lorin","Trying to connect to the server....");
            //Socket socket = new Socket(SERVERADDRESS, PORT);
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(SERVERADDRESS, PORT), 500);
            //Log.e("Lorin","Connected!");
            PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);

            Packet newObj = new Packet(localTank.getMultiplayerInstance(),localBulletContainer.getBulletContainerMultiplayer());
            if (!damageTarget.equals("")) {
                newObj.damagePacket.setDamageTarget(damageTarget);
                damageTarget = "";
            }

            String retString = encodeObject(newObj);
            printWriter.println(retString);
            socket.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    //Recieve data from the server
    public Packet getData() {
        String inputLine;
        Packet packet = null;

        try {
            //Log.e("Lorin","Waiting for connection from server...");
            ServerSocket serverSocket = new ServerSocket(PORT);
            serverSocket.setSoTimeout(500);
            Socket clientSocket = serverSocket.accept();
            //Log.e("Lorin","Done!");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            while((inputLine = bufferedReader.readLine()) != null) {
                packet = decodeObject(inputLine);
            }
            serverSocket.close();

        }
        catch(Exception e) {
            System.out.println(e);
            packet = lastPacket;
        }

        return packet;
    }

    //Encode the data packet sent to the server
    public String encodeObject(Packet packet) {
        String encodedObj = null;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream so = new ObjectOutputStream(bo);
            so.writeObject(packet);
            so.flush();
            encodedObj = new String(Base64.encode(bo.toByteArray(),Base64.NO_WRAP));
        } catch (Exception e) {
            System.out.println(e);
        }
        return encodedObj;
    }

    //Decode the data packet recieved from the server
    public Packet decodeObject(String encodedObj) {
        Packet packet = null;
        try {
            byte b[] = Base64.decode(encodedObj.getBytes(),Base64.DEFAULT);
            ByteArrayInputStream bi = new ByteArrayInputStream(b);
            ObjectInputStream si = new ObjectInputStream(bi);
            packet = (Packet) si.readObject();
        } catch (Exception e) {
            System.out.println(e);
        }
        return packet;
    }

    public void setDamageTarget(String target) {
        Log.e("Lorin","The TANK WAS DAMAGED FROM CLIENT!!!!");
        damageTarget = target;
    }

    private void handleData() {
        if (lastPacket != null) {
            if (lastPacket.damagePacket != null && !lastPacket.damagePacket.getDamageTarget().isEmpty()) {
                localTank.damageTank();
                Log.e("Lorin", "The tank was damaged from server!");
            }
        }
    }

    //Draw all information from the packet onto the screen.
    public void draw(Canvas canvas) {
        if (lastPacket != null) {

            //Draw Tanks
            int counter = 0;
            for (TankPacket tankPacket : lastPacket.otherTankPackets) {
                MultiplayerTank curTank = tanks[counter];
                if (curTank == null) {
                    tanks[counter] = new MultiplayerTank(context, camera, new TankApperance(tankPacket.getSize(),tankPacket.getColor()), tankPacket.getPosition(), tankPacket.clientId, this);
                    curTank = tanks[counter];
                }
                if (curTank.getGoalPos().equals(tankPacket.getPosition())) {
                    curTank.stepPosition();
                } else {
                    curTank.setGoal(tankPacket.getPosition(), tankPacket.getVelocity());
                }
                curTank.draw(canvas);
                counter++;
            }
            Bitmap bulletImage = localBulletContainer.getBulletImage();
            //Draw bullets
            for (BulletMultiplayer bulletMultiplayer : lastPacket.otherBulletContainers.getBullets()) {
                Log.e("Lorin","Drawing multibullet!");
                canvas.drawBitmap(bulletImage, bulletMultiplayer.position.x - camera.getPosition().x, bulletMultiplayer.position.y - camera.getPosition().y, null);
                bulletMultiplayer.move();
            }
        }
    }
}
