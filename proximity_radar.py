import time
import random

def print_header():
    print("="*50)
    print(" PROJECT PROXIMITY: BLE RADAR SIMULATOR")
    print("="*50)

class DeviceNode:
    def __init__(self, mac, device_type):
        self.mac = mac
        self.device_type = device_type
        self.locations_seen = set()
        self.rssi_history = []
        
    def log_ping(self, location, rssi):
        self.locations_seen.add(location)
        self.rssi_history.append(rssi)

    def calculate_threat(self):
        location_count = len(self.locations_seen)
        if len(self.rssi_history) > 0:
            avg_rssi = sum(self.rssi_history) / len(self.rssi_history)
        else:
            avg_rssi = -100

        score = 0.0
        
        if location_count >= 3:
            score += 0.5  
        if -55 <= avg_rssi <= -40:
            score += 0.3  
        if self.device_type == "AirTag" or self.device_type == "SmartTag":
            score += 0.2  
        return score, location_count, avg_rssi

def run_simulation():
    print_header()
    
    devices = [
        DeviceNode("AA:11:BB:22", "AirTag"),      
        DeviceNode("FF:EE:DD:CC", "Headphones"),  
        DeviceNode("99:88:77:66", "Smartwatch")   
    ]
    
    # Simulating the user's journey across 3 locations (Home, Metro, Cafe) Ik this is just a simulation but we have to start somewhere gang
    locations = ["Sector_62_Metro", "Rajiv_Chowk_Transfer", "CP_Coffee_Shop"]
    
    for loc in locations:
        print(f"\n[+] User moved to: {loc}")
        time.sleep(1)
        
        devices[0].log_ping(loc, random.randint(-48, -42))
        
        if loc != "CP_Coffee_Shop":
            devices[1].log_ping(loc, random.randint(-85, -70))
            
        if loc == "CP_Coffee_Shop":
            devices[2].log_ping(loc, random.randint(-65, -55))

    print("\n" + "="*50)
    print("THREAT ANALYSIS RESULTS")
    print("="*50)
    
    for d in devices:
        score, loc_count, avg_rssi = d.calculate_threat()
        
        print(f"Device: {d.mac} ({d.device_type})")
        print(f"Locations Tracked: {loc_count} | Avg RSSI: {avg_rssi:.1f} dBm")
        
        if score >= 0.75:
            print(f"Status: CRITICAL THREAT FLAGGED (Score: {score:.2f})")
        elif score >= 0.4:
            print(f"Status: SUSPICIOUS (Score: {score:.2f})")
        else:
            print(f"Status: SAFE / PASSERBY (Score: {score:.2f})")
        print("-" * 40)

if __name__ == "__main__":
    run_simulation()