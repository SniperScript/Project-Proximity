#include <jni.h>
#include <cstdint>
#include <cstring>

#pragma pack(push, 1)
struct SOSMeshPacket {
    uint32_t victim_id;
    float latitude;
    float longitude;
    uint8_t threat_level;
    uint8_t battery_remaining;
};
#pragma pack(pop)

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_proximity_core_NativeMeshBridge_packSOSPayload(
        JNIEnv* env,
        jobject /* this */,
        jint victimId,
        jfloat latitude,
        jfloat longitude,
        jint threatLevel,
        jint batteryRemaining) {

    SOSMeshPacket packet;
    packet.victim_id = static_cast<uint32_t>(victimId);
    packet.latitude = static_cast<float>(latitude);
    packet.longitude = static_cast<float>(longitude);
    packet.threat_level = static_cast<uint8_t>(threatLevel);
    packet.battery_remaining = static_cast<uint8_t>(batteryRemaining);

    jbyteArray result = env->NewByteArray(sizeof(SOSMeshPacket));
    env->SetByteArrayRegion(result, 0, sizeof(SOSMeshPacket), reinterpret_cast<const jbyte*>(&packet));
    return result;
}