// Agent Pharmacist
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }

!start.

+!start <-
    .print("Duoc si Carol da san sang tai quay thuoc.").

// Thuc hien muc tieu boc thuoc (fill_prescription)
+!fill_prescription[scheme(Sch)] <-
    .print("Duoc si Carol dang chuan bi va dong goi thuoc theo don...");
    .wait(1000);
    .print("Thuoc da duoc cap phat day du cho benh nhan.");
    goalAchieved(fill_prescription).
