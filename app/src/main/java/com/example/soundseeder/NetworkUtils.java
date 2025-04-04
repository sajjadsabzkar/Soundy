package com.example.soundseeder;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkUtils {
    public static void setWifiAPEnabled(Context context, boolean enabled) {
        if (enabled || isWifiApEnabled(context)) {
            if (Utils.shouldSwitchMobileData(context)) {
                setMobileDataState(context, !enabled);
            }
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifi.setWifiEnabled(false);
            Method[] wmMethods = wifi.getClass().getDeclaredMethods();
            for (Method method : wmMethods) {
                if (method.getName().equals("setWifiApEnabled")) {
                    WifiConfiguration netConfig = new WifiConfiguration();
                    netConfig.SSID = Utils.getHotspotName(context);
                    netConfig.allowedAuthAlgorithms.set(0);
                    try {
                        method.invoke(wifi, netConfig, enabled);
                    } catch (Exception e) {
                        Logger.error(e);
                    }
                }
            }
        }
    }

    public static boolean isWifiApEnabled(Context context) {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();

        for (Method method : wmMethods) {
            if (method.getName().equals("isWifiApEnabled")) {
                try {
                    return (boolean) (Boolean) method.invoke(wifi, new Object[0]);
                } catch (Exception e) {
                    Logger.error(e);
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean isWifiEnabled(Context context) {
        return ((WifiManager) context.getSystemService("wifi")).isWifiEnabled();
    }

    public static boolean isWifiConnected(Context context) {
        return ((WifiManager) context.getSystemService("wifi")).getConnectionInfo().getNetworkId() != -1;
    }

    public static String getIpAddress(Context context) {
        if (isWifiEnabled(context) && isWifiConnected(context)) {
            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService("wifi");
                return InetAddress.getByAddress(BigInteger.valueOf(Integer.reverseBytes(wifiManager.getConnectionInfo().getIpAddress())).toByteArray()).getHostAddress();
            } catch (UnknownHostException e) {
                return "Error";
            }
        }
        return "Error";
    }

    public static boolean isMobileDataEnabled(Context context) {
        if (Build.VERSION.SDK_INT < 21) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService("connectivity");
            try {
                Class<?> cmClass = Class.forName(cm.getClass().getName());
                Method method = cmClass.getDeclaredMethod("getMobileDataEnabled", new Class[0]);
                method.setAccessible(true);
                return ((Boolean) method.invoke(cm, new Object[0])).booleanValue();
            } catch (Exception e) {
                return false;
            }
        }
        try {
            TelephonyManager telephonyService = (TelephonyManager) context.getSystemService("phone");
            Method getMobileDataEnabledMethod = telephonyService.getClass().getDeclaredMethod("getDataEnabled", new Class[0]);
            if (getMobileDataEnabledMethod != null) {
                return ((Boolean) getMobileDataEnabledMethod.invoke(telephonyService, new Object[0])).booleanValue();
            }
        } catch (Exception e2) {
        }
        return false;
    }

    public static void setMobileDataState(Context context, boolean enabled) {
        if (Build.VERSION.SDK_INT < 21) {
            try {
                ConnectivityManager conman = (ConnectivityManager) context.getSystemService("connectivity");
                Class<?> conmanClass = Class.forName(conman.getClass().getName());
                Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
                iConnectivityManagerField.setAccessible(true);
                Object iConnectivityManager = iConnectivityManagerField.get(conman);
                Class<?> iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
                Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                setMobileDataEnabledMethod.setAccessible(true);
                setMobileDataEnabledMethod.invoke(iConnectivityManager, Boolean.valueOf(enabled));
                return;
            } catch (Exception e) {
                return;
            }
        }
        try {
            TelephonyManager telephonyService = (TelephonyManager) context.getSystemService("phone");
            Method setMobileDataEnabledMethod2 = telephonyService.getClass().getDeclaredMethod("setDataEnabled", Boolean.TYPE);
            if (setMobileDataEnabledMethod2 != null) {
                setMobileDataEnabledMethod2.invoke(telephonyService, Boolean.valueOf(enabled));
            }
        } catch (Exception e2) {
        }
    }

    public static String getChorusHostId(Context context) {
        if (isWifiEnabled(context) && isWifiConnected(context)) {
            String ipAddressString = getIpAddress(context);
            if (!ipAddressString.startsWith("Error")) {
                return ipAddressString.substring(ipAddressString.lastIndexOf(".") + 1);
            }
            return ipAddressString;
        }
        return "1";
    }

    public static String getChorusHostId(Context context, int id) {
        if (!isWifiEnabled(context) || !isWifiConnected(context)) {
            return null;
        }
        String ip = getIpAddress(context);
        return ip.substring(0, ip.lastIndexOf(".") + 1) + id;
    }
}
