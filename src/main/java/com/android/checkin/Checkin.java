package com.android.checkin;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;
import java.util.Date;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.android.checkin.proto.Checkin.AndroidCheckinRequest;
import com.android.checkin.proto.Checkin.AndroidCheckinResponse;
import com.android.checkin.proto.Checkin.GservicesSetting;
import com.android.checkin.proto.Logs.AndroidCheckinProto;
import com.android.checkin.proto.Logs.AndroidBuildProto;
import com.android.checkin.proto.Logs.AndroidEventProto;
import com.android.checkin.proto.Config.DeviceConfigurationProto;

import com.android.checkin.HttpClientGzip;
import com.android.checkin.Helpers;
import com.android.checkin.Password_Encrypter;

public class Checkin {
    private final HttpClientGzip httpclient = new HttpClientGzip();
    private final Random rand = new Random();
    private final String email;
    private final String password;

    private String token;
    public String getToken() { return this.token; }

    private String LSID;
    public String getLSID() { return this.LSID; }

    private String meid;
    public String getMeid() { return this.meid; }

    private String serial;
    public String getSerial() { return this.serial; }

    private String macAddr;
    public String getMacAddr() { return this.macAddr; }

    private String gsfId;
    public String getGsfId() { return this.gsfId; }

    private long androidId;
    public long getAndroidId() { return this.androidId; }

    private long lastCheckin;

    private long securityToken;

    public Checkin(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String checkin() throws IOException {
        this.meid = generateMeid();
        this.serial = generateSerialNumber();
        this.macAddr = generateMacAddr();

        System.err.println("Checking in without Google account...");
        doCheckin(1);
        System.err.println("Fetching auth (google service)...");
        fetchLSID();
        System.err.println("Checking in...");
        doCheckin(2);

	return this.gsfId;
    }

    // --------------------------------- auth --------------------------------- //

    public void fetchLSID() throws IOException {
        ArrayList<NameValuePair> data = new ArrayList<NameValuePair>();

        data.add(new BasicNameValuePair("accountType",    "HOSTED_OR_GOOGLE"));
        data.add(new BasicNameValuePair("Email",          this.email));
        data.add(new BasicNameValuePair("EncryptedPasswd", Password_Encrypter.encryptPassword(this.email, this.password)));
        data.add(new BasicNameValuePair("has_permission", "1"));
        data.add(new BasicNameValuePair("service",        "ac2dm"));
        data.add(new BasicNameValuePair("source",         "android"));
        data.add(new BasicNameValuePair("app",            "com.google.android.gsf"));
        data.add(new BasicNameValuePair("androidId",      this.gsfId));
        data.add(new BasicNameValuePair("client_sig",     "38918a453d07199354f8b19af05ec6562ced5788"));
        data.add(new BasicNameValuePair("lang",           "en"));
        data.add(new BasicNameValuePair("device_country", "us"));
        data.add(new BasicNameValuePair("operatorCountry","us"));
        data.add(new BasicNameValuePair("sdk_version",    "19"));

        this.LSID = postFormFetchValue("https://android.clients.google.com/auth", data, "LSID");
    }

    private String postFormFetchValue(String url, ArrayList<NameValuePair> params, String key) throws IOException {
        String line;

        HttpPost request = new HttpPost(url);
        request.setHeader("User-Agent", "GoogleLoginService/1.3 (Nexus 4 Build/KOT49H)");
        request.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));

        try {
            HttpResponse response = this.httpclient.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity == null)
                throw new IOException(response.getStatusLine().toString());

            this.token = null;
            Pattern key_value_pattern = Pattern.compile("^([^=]+)=(.+)$");
            BufferedReader body = new BufferedReader(new InputStreamReader(entity.getContent()));
            while ((line = body.readLine()) != null) {
                Matcher m = key_value_pattern.matcher(line);

                //System.err.println(line);

                if (!m.matches())
                    throw new IOException(line + " // " + response.getStatusLine().toString());

                if (key.equals(m.group(1)))
                    return m.group(2);
            }

            throw new IOException("Can't find " + key + " // " + response.getStatusLine().toString());
        } finally {
            request.releaseConnection();
        }
    }

    // --------------------------------- checkin --------------------------------- //

    private String generateMeid() {
        // http://en.wikipedia.org/wiki/International_Mobile_Equipment_Identity
        // We start with a known base, and generate random MEID
        String meid = "35503104";
        for (int i = 0; i < 6; i++)
            meid += Integer.toString(rand.nextInt(10));

        // Luhn algorithm (check digit)
        int sum = 0;
        for (int i = 0; i < meid.length(); i++) {
            int c = Integer.parseInt(String.valueOf(meid.charAt(i)));
            if ((meid.length() - i - 1) % 2 == 0) {
                c *= 2;
                c = c % 10 + c / 10;
            }

            sum += c;
        }
        int check = (100 - sum) % 10;
        meid += Integer.toString(check);

        return meid;
    }

    private String generateMacAddr() {
        String mac = "b407f9";
        for (int i = 0; i < 6; i++)
            mac += Integer.toString(rand.nextInt(16), 16);
        return mac;
    }

    private String generateSerialNumber() {
        String serial = "3933E6";
        for (int i = 0; i < 10; i++)
            serial += Integer.toString(rand.nextInt(16), 16);
        serial = serial.toUpperCase();
        return serial;
    }

    private byte[] generateCheckinPayload(int option) {
        long loggingId = rand.nextLong();

        //System.err.println("Using MEID:      " + meid);
        //System.err.println("Using Serial:    " + serial);
        //System.err.println("Using Mac Addr:  " + macAddr);
        //System.err.println("Using LoggingId: " + loggingId);
        if ( option != 1) {
		return AndroidCheckinRequest.newBuilder()
		    // imei
		    .setId(this.androidId)
		    .setDigest("1-929a0dca0eee55513280171a8585da7dcd3700f8")
		    .setCheckin(AndroidCheckinProto.newBuilder()
			.setBuild(AndroidBuildProto.newBuilder()
			    .setId("motorola/ghost_verizon/ghost:4.4.4/KXA21.12-L1.26/26:user/release-keys")
			    .setProduct("qcom")
			    .setCarrier("motorola")
			    .setRadio("MSM8960PRO_BP_2323.038.123.00R")
			    .setBootloader("0x30B7")
			    .setClient("android-google")
			    .setTimestamp(new Date().getTime()/1000)
			    .setDevice("ghost")
			    .setSdkVersion(19)
			    .setModel("XT1060")
			    .setManufacturer("motorola")
			    .setBuildProduct("ghost_verizon")
			    .setOtaInstalled(false))
			.setLastCheckinMsec(this.lastCheckin)
			.addEvent(AndroidEventProto.newBuilder()
			    .setTag("system_update")
			    .setValue("1536,0,-1,NULL")
			    .setTimeMsec(new Date().getTime()))
			// stat
			// requestedGroup
			.setCellOperator("311480") // Verizon
			.setSimOperator("311480")  // Verizon
			.setRoaming("mobile-notroaming")
			.setUserNumber(0))
		    // desiredBuild
		    .setLocale("en_US")
		    .setLoggingId(loggingId)
		    .addMacAddr(this.macAddr)
		    .setMeid(this.meid)
		    .addAccountCookie("[" + this.email + "]")
		    .addAccountCookie(this.LSID)
		    .setTimeZone("America/New_York")
		    .setSecurityToken(this.securityToken)
		    .setVersion(3)
		    .addOtaCert("71Q6Rn2DDZl1zPDVaaeEHItd")
		    .setSerialNumber(this.serial)
		    // esn
		    .setDeviceConfiguration(DeviceConfigurationProto.newBuilder()
			.setTouchScreen(3)
			.setKeyboard(1)
			.setNavigation(1)
			.setScreenLayout(2)
			.setHasHardKeyboard(false)
			.setHasFiveWayNavigation(false)
			.setScreenDensity(320)
			.setGlEsVersion(19660)
			.addAllSystemSharedLibrary(Arrays.asList(
			    "com.android.future.usb.accessory",
			    "com.android.location.provider",
			    "com.android.nfc_extras",
			    "com.google.android.maps",
			    "com.google.android.media.effects",
			    "com.google.widevine.software.drm",
			    "javax.obex",
			    "org.codeaurora.imslibrary",
			    "org.simalliance.openmobileapi",
			    "com.verizon.phone",
			    ""))
			.addAllSystemAvailableFeature(Arrays.asList(
			    "android.hardware.bluetooth",
			    "android.hardware.camera",
			    "android.hardware.camera.autofocus",
			    "android.hardware.camera.flash",
			    "android.hardware.camera.front",
			    "android.hardware.faketouch",
			    "android.hardware.location",
			    "android.hardware.location.gps",
			    "android.hardware.location.network",
			    "android.hardware.microphone",
			    "android.hardware.nfc",
			    "android.hardware.nfc.hce",
			    "android.hardware.bluetooth_le",
			    "android.hardware.screen.landscape",
			    "android.hardware.screen.portrait",
			    "android.hardware.sensor.accelerometer",
			    "android.hardware.sensor.barometer",
			    "android.hardware.sensor.compass",
			    "android.hardware.sensor.gyroscope",
			    "android.hardware.sensor.light",
			    "android.hardware.sensor.proximity",
			    "android.hardware.telephony",
			    "android.hardware.telephony.gsm",
			    "android.hardware.telephony.cdma",
			    "android.hardware.touchscreen",
			    "android.hardware.touchscreen.multitouch",
			    "android.hardware.touchscreen.multitouch.distinct",
			    "android.hardware.touchscreen.multitouch.jazzhand",
			    "android.hardware.usb.accessory",
			    "android.hardware.usb.host",
			    "android.hardware.wifi",
			    "android.hardware.wifi.direct",
			    "android.software.live_wallpaper",
			    "android.software.sip",
			    "android.software.sip.voip",
			    "android.software.app_widgets",
			    "android.software.device_admin",
			    "android.software.home_screen",
			    "android.software.input_methods",
			    "com.nxp.mifare"))
			.addAllNativePlatform(Arrays.asList(
			    "armeabi-v7a",
			    "armeabi"))
			.setScreenWidth(720)
			.setScreenHeight(1184)
			.addAllSystemSupportedLocale(Arrays.asList(
			    "af", "af_ZA", "am", "am_ET", "ar", "ar_EG", "bg", "bg_BG",
			    "ca", "ca_ES", "cs", "cs_CZ", "da", "da_DK", "de", "de_AT",
			    "de_CH", "de_DE", "de_LI", "el", "el_GR", "en", "en_AU",
			    "en_CA", "en_GB", "en_NZ", "en_SG", "en_US", "es", "es_ES",
			    "es_US", "fa", "fa_IR", "fi", "fi_FI", "fr", "fr_BE",
			    "fr_CA", "fr_CH", "fr_FR", "hi", "hi_IN", "hr", "hr_HR",
			    "hu", "hu_HU", "in", "in_ID", "it", "it_CH", "it_IT", "iw",
			    "iw_IL", "ja", "ja_JP", "ko", "ko_KR", "lt", "lt_LT", "lv",
			    "lv_LV", "ms", "ms_MY", "nb", "nb_NO", "nl", "nl_BE",
			    "nl_NL", "pl", "pl_PL", "pt", "pt_BR", "pt_PT", "rm",
			    "rm_CH", "ro", "ro_RO", "ru", "ru_RU", "sk", "sk_SK", "sl",
			    "sl_SI", "sr", "sr_RS", "sv", "sv_SE", "sw", "sw_TZ", "th",
			    "th_TH", "tl", "tl_PH", "tr", "tr_TR", "ug", "ug_CN", "uk",
			    "uk_UA", "vi", "vi_VN", "zh_CN", "zh_TW", "zu", "zu_ZA"))
			.addAllGlExtension(Arrays.asList(
			    "GL_AMD_compressed_ATC_texture",
			    "GL_AMD_performance_monitor",
			    "GL_AMD_program_binary_Z400",
			    "GL_APPLE_texture_2D_limited_npot",
			    "GL_ARB_vertex_buffer_object",
			    "GL_EXT_color_buffer_float",
			    "GL_EXT_color_buffer_half_float",
			    "GL_EXT_debug_label",
			    "GL_EXT_debug_marker",
			    "GL_EXT_discard_framebuffer",
			    "GL_EXT_disjoint_timer_query",
			    "GL_EXT_multisampled_render_to_texture",
			    "GL_EXT_robustness",
			    "GL_EXT_sRGB",
			    "GL_EXT_sRGB_write_control",
			    "GL_EXT_texture_filter_anisotropic",
			    "GL_EXT_texture_format_BGRA8888",
			    "GL_EXT_texture_sRGB_decode",
			    "GL_EXT_texture_type_2_10_10_10_REV",
			    "GL_NV_fence",
			    "GL_OES_EGL_image",
			    "GL_OES_EGL_image_external",
			    "GL_OES_blend_equation_separate",
			    "GL_OES_blend_func_separate",
			    "GL_OES_blend_subtract",
			    "GL_OES_compressed_ETC1_RGB8_texture",
			    "GL_OES_compressed_paletted_texture",
			    "GL_OES_depth24",
			    "GL_OES_depth_texture",
			    "GL_OES_depth_texture_cube_map",
			    "GL_OES_draw_texture",
			    "GL_OES_element_index_uint",
			    "GL_OES_fbo_render_mipmap",
			    "GL_OES_fragment_precision_high",
			    "GL_OES_framebuffer_object",
			    "GL_OES_get_program_binary",
			    "GL_OES_matrix_palette",
			    "GL_OES_packed_depth_stencil",
			    "GL_OES_point_size_array",
			    "GL_OES_point_sprite",
			    "GL_OES_read_format",
			    "GL_OES_rgb8_rgba8",
			    "GL_OES_standard_derivatives",
			    "GL_OES_stencil_wrap",
			    "GL_OES_texture_3D",
			    "GL_OES_texture_cube_map",
			    "GL_OES_texture_env_crossbar",
			    "GL_OES_texture_float",
			    "GL_OES_texture_half_float",
			    "GL_OES_texture_half_float_linear",
			    "GL_OES_texture_mirrored_repeat",
			    "GL_OES_texture_npot",
			    "GL_OES_vertex_array_object",
			    "GL_OES_vertex_half_float",
			    "GL_OES_vertex_type_10_10_10_2",
			    "GL_QCOM_alpha_test",
			    "GL_QCOM_binning_control",
			    "GL_QCOM_driver_control",
			    "GL_QCOM_extended_get",
			    "GL_QCOM_extended_get2",
			    "GL_QCOM_perfmon_global_mode",
			    "GL_QCOM_tiled_rendering",
			    "GL_QCOM_writeonly_rendering")))
			// deviceClass
			// maxApkDownloadSizeMb
		    .addMacAddrType("wifi")
		    .setFragment(0)
		    // userName
		    .build()
		    .toByteArray();
        }
        else {
                this.lastCheckin = new Date().getTime();
		return AndroidCheckinRequest.newBuilder()
		    // imei
		    .setId(0)
		    .setDigest("1-929a0dca0eee55513280171a8585da7dcd3700f8")
		    .setCheckin(AndroidCheckinProto.newBuilder()
			.setBuild(AndroidBuildProto.newBuilder()
			    .setId("motorola/ghost_verizon/ghost:4.4.4/KXA21.12-L1.26/26:user/release-keys")
			    .setProduct("qcom")
			    .setCarrier("motorola")
			    .setRadio("MSM8960PRO_BP_2323.038.123.00R")
			    .setBootloader("0x30B7")
			    .setClient("android-google")
			    .setTimestamp(new Date().getTime()/1000)
			    .setDevice("ghost")
			    .setSdkVersion(19)
			    .setModel("XT1060")
			    .setManufacturer("motorola")
			    .setBuildProduct("ghost_verizon")
			    .setOtaInstalled(false))
			.setLastCheckinMsec(0)
			.addEvent(AndroidEventProto.newBuilder()
			    .setTag("event_log_start")
			    // value
			    .setTimeMsec(this.lastCheckin))
			// stat
			// requestedGroup
			.setCellOperator("311480") // Verizon
			.setSimOperator("311480")  // Verizon
			.setRoaming("mobile-notroaming")
			.setUserNumber(0))
		    // desiredBuild
		    .setLocale("en_US")
		    .setLoggingId(loggingId)
		    .addMacAddr(this.macAddr)
		    .setMeid(this.meid)
		    .addAccountCookie("")
		    .setTimeZone("America/New_York")
		    // securityToken
		    .setVersion(3)
		    .addOtaCert("71Q6Rn2DDZl1zPDVaaeEHItd")
		    .setSerialNumber(this.serial)
		    // esn
		    .setDeviceConfiguration(DeviceConfigurationProto.newBuilder()
			.setTouchScreen(3)
			.setKeyboard(1)
			.setNavigation(1)
			.setScreenLayout(2)
			.setHasHardKeyboard(false)
			.setHasFiveWayNavigation(false)
			.setScreenDensity(320)
			.setGlEsVersion(19660)
			.addAllSystemSharedLibrary(Arrays.asList(
			    "com.android.future.usb.accessory",
			    "com.android.location.provider",
			    "com.android.nfc_extras",
			    "com.google.android.maps",
			    "com.google.android.media.effects",
			    "com.google.widevine.software.drm",
			    "javax.obex",
			    "org.codeaurora.imslibrary",
			    "org.simalliance.openmobileapi",
			    "com.verizon.phone",
			    ""))
			.addAllSystemAvailableFeature(Arrays.asList(
			    "android.hardware.bluetooth",
			    "android.hardware.camera",
			    "android.hardware.camera.autofocus",
			    "android.hardware.camera.flash",
			    "android.hardware.camera.front",
			    "android.hardware.faketouch",
			    "android.hardware.location",
			    "android.hardware.location.gps",
			    "android.hardware.location.network",
			    "android.hardware.microphone",
			    "android.hardware.nfc",
			    "android.hardware.nfc.hce",
			    "android.hardware.bluetooth_le",
			    "android.hardware.screen.landscape",
			    "android.hardware.screen.portrait",
			    "android.hardware.sensor.accelerometer",
			    "android.hardware.sensor.barometer",
			    "android.hardware.sensor.compass",
			    "android.hardware.sensor.gyroscope",
			    "android.hardware.sensor.light",
			    "android.hardware.sensor.proximity",
			    "android.hardware.telephony",
			    "android.hardware.telephony.gsm",
			    "android.hardware.touchscreen",
			    "android.hardware.touchscreen.multitouch",
			    "android.hardware.touchscreen.multitouch.distinct",
			    "android.hardware.touchscreen.multitouch.jazzhand",
			    "android.hardware.usb.accessory",
			    "android.hardware.usb.host",
			    "android.hardware.wifi",
			    "android.hardware.wifi.direct",
			    "android.software.live_wallpaper",
			    "android.software.sip",
			    "android.software.sip.voip",
			    "android.software.app_widgets",
			    "android.software.device_admin",
			    "android.software.home_screen",
			    "android.software.input_methods"))
			.addAllNativePlatform(Arrays.asList(
			    "armeabi-v7a",
			    "armeabi"))
			.setScreenWidth(720)
			.setScreenHeight(1184)
			.addAllSystemSupportedLocale(Arrays.asList(
			    "af", "af_ZA", "am", "am_ET", "ar", "ar_EG", "bg", "bg_BG",
			    "ca", "ca_ES", "cs", "cs_CZ", "da", "da_DK", "de", "de_AT",
			    "de_CH", "de_DE", "de_LI", "el", "el_GR", "en", "en_AU",
			    "en_CA", "en_GB", "en_NZ", "en_SG", "en_US", "es", "es_ES",
			    "es_US", "fa", "fa_IR", "fi", "fi_FI", "fr", "fr_BE",
			    "fr_CA", "fr_CH", "fr_FR", "hi", "hi_IN", "hr", "hr_HR",
			    "hu", "hu_HU", "in", "in_ID", "it", "it_CH", "it_IT", "iw",
			    "iw_IL", "ja", "ja_JP", "ko", "ko_KR", "lt", "lt_LT", "lv",
			    "lv_LV", "ms", "ms_MY", "nb", "nb_NO", "nl", "nl_BE",
			    "nl_NL", "pl", "pl_PL", "pt", "pt_BR", "pt_PT", "rm",
			    "rm_CH", "ro", "ro_RO", "ru", "ru_RU", "sk", "sk_SK", "sl",
			    "sl_SI", "sr", "sr_RS", "sv", "sv_SE", "sw", "sw_TZ", "th",
			    "th_TH", "tl", "tl_PH", "tr", "tr_TR", "ug", "ug_CN", "uk",
			    "uk_UA", "vi", "vi_VN", "zh_CN", "zh_TW", "zu", "zu_ZA"))
			.addAllGlExtension(Arrays.asList(
			    "GL_AMD_compressed_ATC_texture",
			    "GL_AMD_performance_monitor",
			    "GL_AMD_program_binary_Z400",
			    "GL_APPLE_texture_2D_limited_npot",
			    "GL_ARB_vertex_buffer_object",
			    "GL_EXT_color_buffer_float",
			    "GL_EXT_color_buffer_half_float",
			    "GL_EXT_debug_label",
			    "GL_EXT_debug_marker",
			    "GL_EXT_discard_framebuffer",
			    "GL_EXT_disjoint_timer_query",
			    "GL_EXT_multisampled_render_to_texture",
			    "GL_EXT_robustness",
			    "GL_EXT_sRGB",
			    "GL_EXT_sRGB_write_control",
			    "GL_EXT_texture_filter_anisotropic",
			    "GL_EXT_texture_format_BGRA8888",
			    "GL_EXT_texture_sRGB_decode",
			    "GL_EXT_texture_type_2_10_10_10_REV",
			    "GL_NV_fence",
			    "GL_OES_EGL_image",
			    "GL_OES_EGL_image_external",
			    "GL_OES_blend_equation_separate",
			    "GL_OES_blend_func_separate",
			    "GL_OES_blend_subtract",
			    "GL_OES_compressed_ETC1_RGB8_texture",
			    "GL_OES_compressed_paletted_texture",
			    "GL_OES_depth24",
			    "GL_OES_depth_texture",
			    "GL_OES_depth_texture_cube_map",
			    "GL_OES_draw_texture",
			    "GL_OES_element_index_uint",
			    "GL_OES_fbo_render_mipmap",
			    "GL_OES_fragment_precision_high",
			    "GL_OES_framebuffer_object",
			    "GL_OES_get_program_binary",
			    "GL_OES_matrix_palette",
			    "GL_OES_packed_depth_stencil",
			    "GL_OES_point_size_array",
			    "GL_OES_point_sprite",
			    "GL_OES_read_format",
			    "GL_OES_rgb8_rgba8",
			    "GL_OES_standard_derivatives",
			    "GL_OES_stencil_wrap",
			    "GL_OES_texture_3D",
			    "GL_OES_texture_cube_map",
			    "GL_OES_texture_env_crossbar",
			    "GL_OES_texture_float",
			    "GL_OES_texture_half_float",
			    "GL_OES_texture_half_float_linear",
			    "GL_OES_texture_mirrored_repeat",
			    "GL_OES_texture_npot",
			    "GL_OES_vertex_array_object",
			    "GL_OES_vertex_half_float",
			    "GL_OES_vertex_type_10_10_10_2",
			    "GL_QCOM_alpha_test",
			    "GL_QCOM_binning_control",
			    "GL_QCOM_driver_control",
			    "GL_QCOM_extended_get",
			    "GL_QCOM_extended_get2",
			    "GL_QCOM_perfmon_global_mode",
			    "GL_QCOM_tiled_rendering",
			    "GL_QCOM_writeonly_rendering")))
			// deviceClass
			// maxApkDownloadSizeMb
		    .addMacAddrType("wifi")
		    .setFragment(0)
		    // userName
		    .build()
		    .toByteArray();
        }
    }

    public void doCheckin(int option) throws IOException {
        HttpPost request = new HttpPost("https://android.clients.google.com/checkin");
        request.setHeader("Content-type", "application/x-protobuffer");
        request.setHeader("Content-Encoding", "gzip");
        request.setHeader("Accept-Encoding", "gzip");
        request.setHeader("User-Agent", "Android-Checkin/2.0 (vbox86p JLS36G); gzip");

        request.setEntity(new ByteArrayEntity(generateCheckinPayload(option)));

        try {
            HttpResponse response = this.httpclient.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity == null)
                throw new IOException(response.getStatusLine().toString());

            byte[] response_bytes = Helpers.inputStreamToBytes(entity.getContent());
            AndroidCheckinResponse parsed_response = AndroidCheckinResponse.parseFrom(response_bytes);

            long gsfid = parsed_response.getGsfId();
            if (gsfid == 0)
                throw new IOException("Can't find gsf_id" + " // " + response.getStatusLine().toString());

            if (option == 1) {
                this.gsfId = Long.toString(gsfid, 16);

                this.securityToken = parsed_response.getSecurityToken();
                System.err.println("security token: " + Long.toString(this.securityToken, 10));

                for (GservicesSetting s : parsed_response.getSettingList()) {
                    if ((s.getName()).toStringUtf8().equals("android_id")) {
                        this.androidId = Long.parseLong((s.getValue()).toStringUtf8(), 10);
                    }
                }
                System.err.println("android_id: " + Long.toString(this.androidId, 10));
            }
            else {
            }
        } finally {
            request.releaseConnection();
        }
    }
}
