#include <iostream>
#include <cstring>
#include <iomanip>
#include <cstdint>

struct SOSPayload {         // Remember
    uint32_t user_id;       // 4 bytes 
    float latitude;          
    float longitude;        
    uint8_t threat_level;   // 1 byte  (0: Safe, 1: Suspicious, 2: Critical)
    uint8_t battery_level;  // 1 byte  
} __attribute__((packed));

void compress_for_mesh(const SOSPayload& input, uint8_t* output_buffer) {
    std::memcpy(output_buffer, &input, sizeof(SOSPayload));
}

SOSPayload decode_from_mesh(const uint8_t* input_buffer) {
    SOSPayload output;
    std::memcpy(&output, input_buffer, sizeof(SOSPayload));
    return output;
}

int main() {
    std::cout << "--- Project Proximity: Mesh Serialization Engine ---\n\n";

    SOSPayload original_alert = {
        8829102,     
        28.6280f,    
        77.2191f,    
        2,           // Threat Level 2 
        12           // 12% Battery Remaining
    };

    uint8_t mesh_packet[sizeof(SOSPayload)];
    
    compress_for_mesh(original_alert, mesh_packet);

    std::cout << "[+] Payload Compressed for Wi-Fi Aware Broadcast.\n";
    std::cout << "[+] Raw Hex Data Transmitted Over The Air:\n    ";
    
    for(size_t i = 0; i < sizeof(SOSPayload); ++i) {
        std::cout << std::hex << std::setw(2) << std::setfill('0') << (int)mesh_packet[i] << " ";
    }
    std::cout << "\n\n";

    SOSPayload decoded_alert = decode_from_mesh(mesh_packet);

    std::cout << "[+] Packet Decoded at Rescue Node:\n";
    std::cout << "    User ID: " << std::dec << decoded_alert.user_id << "\n";
    std::cout << "    GPS: " << decoded_alert.latitude << ", " << decoded_alert.longitude << "\n";
    std::cout << "    Threat Level: " << (int)decoded_alert.threat_level << "\n";
    std::cout << "    Battery: " << (int)decoded_alert.battery_level << "%\n";

    return 0;
}