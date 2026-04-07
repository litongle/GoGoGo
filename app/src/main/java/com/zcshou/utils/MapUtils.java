package com.zcshou.utils;

import java.util.Locale;

public final class MapUtils {
    public static final String COORD_TYPE_WGS84 = "wgs84";
    public static final String COORD_TYPE_GCJ02 = "gcj02";
    public static final String COORD_TYPE_BD09 = "bd09";

    private static final double PI = 3.14159265358979324;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    private static final double X_PI = PI * 3000.0 / 180.0;

    private MapUtils() {
    }

    public static String normalizeCoordType(String coordType) {
        if (coordType == null) {
            return COORD_TYPE_BD09;
        }

        String normalized = coordType.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case COORD_TYPE_WGS84:
            case COORD_TYPE_GCJ02:
            case COORD_TYPE_BD09:
                return normalized;
            case "gps":
                return COORD_TYPE_WGS84;
            default:
                return COORD_TYPE_BD09;
        }
    }

    public static double[] toGcj02(double lng, double lat, String coordType) {
        switch (normalizeCoordType(coordType)) {
            case COORD_TYPE_WGS84:
                return wgs84ToGcj02(lng, lat);
            case COORD_TYPE_GCJ02:
                return new double[]{lng, lat};
            case COORD_TYPE_BD09:
            default:
                return bd09ToGcj02(lng, lat);
        }
    }

    public static double[] toWgs84(double lng, double lat, String coordType) {
        switch (normalizeCoordType(coordType)) {
            case COORD_TYPE_GCJ02:
                return gcj02ToWgs84(lng, lat);
            case COORD_TYPE_BD09:
                return bd09ToWgs84(lng, lat);
            case COORD_TYPE_WGS84:
            default:
                return new double[]{lng, lat};
        }
    }

    public static double[] bd2wgs(double lon, double lat) {
        return bd09ToWgs84(lon, lat);
    }

    public static double[] wgs2bd09(double lng, double lat) {
        return wgs84ToBd09(lng, lat);
    }

    public static double[] bd09togcj02(double bdLon, double bdLat) {
        return bd09ToGcj02(bdLon, bdLat);
    }

    public static double[] gcj02towgs84(double lng, double lat) {
        return gcj02ToWgs84(lng, lat);
    }

    public static double[] bd09ToWgs84(double lng, double lat) {
        double[] gcj = bd09ToGcj02(lng, lat);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    public static double[] wgs84ToBd09(double lng, double lat) {
        double[] gcj = wgs84ToGcj02(lng, lat);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    public static double[] bd09ToGcj02(double bdLon, double bdLat) {
        double x = bdLon - 0.0065;
        double y = bdLat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        double ggLng = z * Math.cos(theta);
        double ggLat = z * Math.sin(theta);
        return new double[]{ggLng, ggLat};
    }

    public static double[] gcj02ToBd09(double lng, double lat) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
        double bdLng = z * Math.cos(theta) + 0.0065;
        double bdLat = z * Math.sin(theta) + 0.006;
        return new double[]{bdLng, bdLat};
    }

    public static double[] gcj02ToWgs84(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }

        double[] delta = delta(lng, lat);
        return new double[]{lng - delta[0], lat - delta[1]};
    }

    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }

        double[] delta = delta(lng, lat);
        return new double[]{lng + delta[0], lat + delta[1]};
    }

    private static double[] delta(double lng, double lat) {
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLon(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{dLng, dLat};
    }

    private static boolean outOfChina(double lng, double lat) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double lng, double lat) {
        double ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lat * PI) + 40.0 * Math.sin(lat / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(lat / 12.0 * PI) + 320 * Math.sin(lat * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double lng, double lat) {
        double ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lng * PI) + 40.0 * Math.sin(lng / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(lng / 12.0 * PI) + 300.0 * Math.sin(lng / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
