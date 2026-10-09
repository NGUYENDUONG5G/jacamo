// Agent Doctor
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

// Thuc hien muc tieu ke don thuoc (prescribe)
+!prescribe[scheme(Sch)] <-
    .wait(1000);
    goalAchieved(prescribe).

// Lang nghe tin hieu yeu cau khoi tao Scheme phuc hoi tu FaultTolerantSchemeBoard
+recovery_required("reconsult_scheme", FailedGoal, ErrorId, Args) <-
    .wait(1500);
    // Phuc hoi Goal cho benh nhan
    resumeGoal(FailedGoal).
