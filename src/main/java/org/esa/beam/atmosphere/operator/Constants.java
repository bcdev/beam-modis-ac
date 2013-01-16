package org.esa.beam.atmosphere.operator;

/**
 * MODIS Atmospheric Correction constants
 *
 * @author olafd
 */
public class Constants {

    public static final String MODIS_ATMOSPHERIC_NET_NAME = "atmo_correct_modis/31x47x37_4689.5.net";

//    public static final String MODIS_TOA_BAND_NAME_PREFIX = "MODIS_SWATH_Type_L1B/Data Fields/EV_1KM_RefSB_Band_1KM_";
    public static final String MODIS_TOA_BAND_NAME_PREFIX = "EV_1KM_";
    public static final String MODIS_GEO_DATAFIELDS_BAND_NAME_PREFIX = "MODIS_Swath_Type_GEO/Data Fields/";
    public static final String MODIS_GEO_GEOLOCATION_BAND_NAME_PREFIX = "MODIS_Swath_Type_GEO/Geolocation Fields/";

    public static final String MODIS_L2_FLAG_BAND_NAME = "L2Flags";
    public static final String AC_FLAG_BAND_NAME = "ac_flags";

    public static final String MODIS_LATITUDE_BAND_NAME = "Latitude";
    public static final String MODIS_LONGITUDE_BAND_NAME = "Longitude";
    public static final String MODIS_SUN_ZENITH_BAND_NAME = "SolarZenith";
    public static final String MODIS_SUN_AZIMUTH_BAND_NAME = "SolarAzimuth";
    public static final String MODIS_VIEW_ZENITH_BAND_NAME = "SensorZenith";
    public static final String MODIS_VIEW_AZIMUTH_BAND_NAME = "SensorAzimuth";

    // The 1km spectral bands in the MODIS L1b LAC product:
    public static final String[] MODIS_SPECTRAL_BAND_NAMES = {
            "RefSB_8",
            "RefSB_9",
            "RefSB_10",
            "RefSB_11",
            "RefSB_12",
            "RefSB_13lo",
            "RefSB_13hi",
            "RefSB_14lo",
            "RefSB_14hi",
            "RefSB_15",
            "RefSB_16",
            "RefSB_17",
            "RefSB_18",
            "RefSB_19",
            "RefSB_26"
    };

//    public static final String[] MODIS_SPECTRAL_BAND_NAMES = {
//            "RefSB1",
//            "RefSB2",
//            "RefSB3",
//            "RefSB4",
//            "RefSB5",
//            "RefSB6",
//            "RefSB7",
//            "RefSB8",
//            "RefSB9",
//            "RefSB10",
//            "RefSB11",
//            "RefSB12",
//            "RefSB13",
//            "RefSB14",
//            "RefSB15"
//    };


    // The names of the spectral bands to use as input for NN approach:
    public static final String[] MODIS_SPECTRAL_BANDNAMES_TO_USE = {
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_8",     // 412nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_9",     // 443nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_10",    // 488nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_11",    // 531nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_12",    // 547nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_13lo",  // 667nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_14lo",  // 678nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_15",    // 748nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_16"     // 869nm
    };
//    public static final String[] MODIS_SPECTRAL_BANDNAMES_TO_USE = {
//            "RefSB1",     // 412nm
//            "RefSB2",     // 443nm
//            "RefSB3",     // 488nm
//            "RefSB4",     // 531nm
//            "RefSB5",     // 547nm
//            "RefSB6",     // 667nm
//            "RefSB8",     // 678nm
//            "RefSB10",    // 748nm
//            "RefSB11"     // 869nm
//    };


    // The wavelengths of the spectral bands to use as input for NN approach:
    public static final double[] MODIS_SPECTRAL_WAVELENGHTS_TO_USE = {
            412.0,
            443.0,
            488.0,
            531.0,
            547.0,
            667.0,
            678.0,
            748.0,
            869.0
    };

    // Solar fluxes in mW/cm^2/um, from http://oceancolor.gsfc.nasa.gov/DOCS/RSR/f0.txt
    public static final double[] SOLAR_FLUXES_TO_USE = {
            167.280,     // 412nm
            195.407,     // 443nm
            191.606,     // 488nm
            191.418,     // 531nm
            187.408,     // 547nm
            151.773,     // 667nm
            148.193,     // 678nm
            128.825,     // 748nm
            95.4836      // 869nm
    };

    // Output reflectance band names
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

    // Output TOSA reflectance band names
    public static final String[] MODIS_TOSA_REFLEC_BAND_NAMES = {
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
