package org.esa.beam.atmosphere.operator;

/**
 * MODIS Atmospheric Correction constants
 *
 * @author olafd
 */
public class Constants {

    public static final String MODIS_ATMOSPHERIC_NET_NAME = "atmo_correct_modis/31x47x37_4689.5.net";

    public static final String MODIS_GEO_BAND_NAME_PREFIX = "MODIS_Swath_Type_GEO/Data Fields/";

    public static final String MODIS_FLAG_BAND_NAME = "L2Flags";

    public static final String MODIS_LATITUDE_BAND_NAME = "Latitude";
    public static final String MODIS_LONGITUDE_BAND_NAME = "Longitude";
    public static final String MODIS_SUN_ZENITH_BAND_NAME = "SolarZenith";
    public static final String MODIS_SUN_AZIMUTH_BAND_NAME = "SolarAzimuth";
    public static final String MODIS_VIEW_ZENITH_BAND_NAME = "ViewZenith";
    public static final String MODIS_VIEW_AZIMUTH_BAND_NAME = "ViewAzimuth";


    public static final String[] MODIS_SPECTRAL_BAND_NAMES = { // todo
            "EV_1KM_RefSB_8",
            "EV_1KM_RefSB_9",
            "EV_1KM_RefSB_10",
            "EV_1KM_RefSB_11",
            "EV_1KM_RefSB_12",
            "EV_1KM_RefSB_13lo",
            "EV_1KM_RefSB_13hi",
            "EV_1KM_RefSB_14lo",
            "EV_1KM_RefSB_14hi",
            "EV_1KM_RefSB_15",
            "EV_1KM_RefSB_16",
            "EV_1KM_RefSB_17",
            "EV_1KM_RefSB_18",
            "EV_1KM_RefSB_19",
            "EV_1KM_RefSB_26"
    };

    public static final String[] MODIS_REFLEC_BAND_NAMES = {
            "refl_412",
            "refl_443",
            "refl_489",
            "refl_531",
            "refl_551",
            "refl_665",
            "refl_678",
            "refl_748",
            "refl_869"
    };

    public static final String[] MODIS_TOSA_BAND_NAMES = {
            "tosa_412",
            "tosa_443",
            "tosa_489",
            "tosa_531",
            "tosa_551",
            "tosa_665",
            "tosa_678",
            "tosa_748",
            "tosa_869"
    };
}
