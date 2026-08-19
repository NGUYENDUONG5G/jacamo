package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;


public class DustbinArtifact extends Artifact {

    @OPERATION
    public void init(String binLocation) {
        defineObsProperty("bin_location", binLocation);
        defineObsProperty("bin_capacity", 100);
        defineObsProperty("bin_fill_level", 10);
        System.out.println("[DustbinArtifact] External dustbin initialized at location: " + binLocation);
    }

    @OPERATION
    public void empty_robot_dust() {
        ObsProperty fillProp = getObsProperty("bin_fill_level");
        if (fillProp != null) {
            int current = fillProp.intValue();
            fillProp.updateValue(Math.min(100, current + 15));
            System.out.println("[DustbinArtifact] Robot dust compartment emptied into external dustbin. Current bin fill: " + (current + 15) + "%");
            signal("dust_emptied");
        }
    }
}
