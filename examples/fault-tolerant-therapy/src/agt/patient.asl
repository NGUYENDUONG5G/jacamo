// Agent Patient
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

// Beliefs
early_recovery_reason("no_more_symptoms").

// Thuc hien muc tieu kham benh (consult)
+!consult[scheme(Sch)] <-
    .wait(1000).

// Thuc hien muc tieu theo doi lieu trinh (follow_therapy)
+!follow_therapy[scheme(Sch)] <-
    .wait(1000);
    .wait(1000);
    // Chia se belief len Board de kich hoat phat hien ngoai le (het trieu chung som vao ngay 3)
    updateOrgBelief("symptoms_cleared(alice, 3)");
    // Sau khi recovery hoan tat, Board tu dong resume va Alice chay tiep:
    .wait(2000).
