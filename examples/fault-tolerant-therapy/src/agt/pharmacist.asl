// Agent Pharmacist
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

// Thuc hien muc tieu boc thuoc (fill_prescription)
+!fill_prescription[scheme(Sch)] <-
    .wait(1000);
    goalAchieved(fill_prescription).
