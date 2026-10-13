package com.z33eculab;

/** Offline SAE-generic subset only; manufacturer-specific codes deliberately fall back. */
public final class DtcDescriptions {
    private DtcDescriptions() {}
    private static final String FALLBACK="Description not bundled";
    public static String describe(String code) {
        if(code==null || !code.matches("(?:P[02]|[CBU]0)[0-9A-F]{3}") || !code.substring(1).matches("[0-9]{4}")) return FALLBACK;
        String p=code.substring(0,1); int n=Integer.parseInt(code.substring(1));
        if("P".equals(p)) return powertrain(n);
        if("C".equals(p)) return chassis(n);
        if("B".equals(p)) return body(n);
        return network(n);
    }
    private static String powertrain(int n) {
        if(n==300)return "Random/multiple cylinder misfire detected";
        if(n>=301&&n<=312)return "Cylinder "+(n-300)+" misfire detected";
        if(n>=201&&n<=212)return "Cylinder "+(n-200)+" injector circuit malfunction";
        if(n>=351&&n<=362)return "Cylinder "+(n-350)+" ignition coil primary/secondary circuit";
        if(n>=170&&n<=175)return n==170?"Fuel trim malfunction (bank 1)":n==171?"System too lean (bank 1)":n==172?"System too rich (bank 1)":n==173?"Fuel trim malfunction (bank 2)":n==174?"System too lean (bank 2)":"System too rich (bank 2)";
        if(n==128)return "Coolant temperature below thermostat regulating temperature";
        if(n==420||n==430)return "Catalyst system efficiency below threshold (bank "+(n==420?1:2)+")";
        String[] faults={"circuit malfunction","circuit range/performance","circuit low input","circuit high input","circuit intermittent"};
        int[] bases={100,105,110,115,120,220}; String[] names={"Mass/volume air flow","Manifold absolute/barometric pressure","Intake air temperature","Engine coolant temperature","Throttle/pedal position sensor A","Throttle/pedal position sensor B"};
        for(int i=0;i<bases.length;i++)if(n>=bases[i]&&n<=bases[i]+4)return names[i]+" "+faults[n-bases[i]];
        int[] o2={130,136,142,150,156,162}; String[] oxygen={"circuit malfunction","circuit low voltage","circuit high voltage","circuit slow response","no activity detected","heater circuit malfunction"};
        for(int i=0;i<o2.length;i++)if(n>=o2[i]&&n<=o2[i]+5)return "O2 sensor "+oxygen[n-o2[i]]+" (bank "+(i<3?1:2)+", sensor "+(i%3+1)+")";
        switch(n){
            case 1:return "Fuel volume regulator control circuit/open"; case 10:return "A camshaft position actuator circuit/open (bank 1)";
            case 123:return "Intake air temperature sensor circuit high input"; case 125:return "Insufficient coolant temperature for closed loop fuel control";
            case 126:return "Turbocharger/supercharger boost sensor range/performance"; case 140:return "O2 sensor heater circuit malfunction (bank 1, sensor 2)";
            case 145:return "O2 sensor heater circuit malfunction (bank 1, sensor 3)"; case 160:return "O2 sensor circuit malfunction (bank 2, sensor 2)";
            case 200:return "Injector circuit/open"; case 325:return "Knock sensor 1 circuit malfunction";
            case 335:return "Crankshaft position sensor A circuit malfunction"; case 340:return "Camshaft position sensor A circuit malfunction (bank 1)";
            case 219:return "Engine overspeed condition"; case 240:return "Turbocharger/supercharger boost sensor B range/performance";
            case 440:return "Evaporative emission control system malfunction"; case 441:return "Evaporative emission system incorrect purge flow"; case 442:return "Evaporative emission system leak detected (small leak)";
            case 443:return "Evaporative emission system purge control valve circuit"; case 444:return "Evaporative emission system purge control valve circuit open";
            case 445:return "Evaporative emission system purge control valve circuit shorted"; case 450:return "Evaporative emission system pressure sensor";
            case 455:return "Evaporative emission system leak detected (gross leak)"; case 456:return "Evaporative emission system leak detected (very small leak)";
            case 500:return "Vehicle speed sensor malfunction"; case 507:return "Idle control system RPM higher than expected";
            case 560:return "System voltage malfunction"; case 700:return "Transmission control system malfunction";
            case 715:return "Input/turbine speed sensor circuit malfunction"; case 720:return "Output speed sensor circuit malfunction";
            case 730:return "Incorrect gear ratio"; case 740:return "Torque converter clutch circuit/open";
            default:return FALLBACK;
        }
    }
    private static String chassis(int n){switch(n){case 35:return "Left front wheel speed sensor circuit";case 40:return "Right front wheel speed sensor circuit";case 45:return "Left rear wheel speed sensor circuit";case 50:return "Right rear wheel speed sensor circuit";case 110:return "ABS pump motor circuit";default:return FALLBACK;}}
    private static String body(int n){switch(n){case 1:return "Driver frontal stage 1 deployment control";default:return FALLBACK;}}
    private static String network(int n){switch(n){case 100:return "Lost communication with ECM/PCM";case 101:return "Lost communication with TCM";case 121:return "Lost communication with ABS control module";case 140:return "Lost communication with body control module";default:return FALLBACK;}}
}
