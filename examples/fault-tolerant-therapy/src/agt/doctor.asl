// Agent Doctor
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }

!start.

+!start <-
    .print("Bac si Bob da san sang tai phong kham.").

// Thuc hien muc tieu ke don thuoc (prescribe)
+!prescribe[scheme(Sch)] <-
    .print("Bac si Bob dang ke don thuoc cho benh nhan...");
    .wait(1000);
    .print("Don thuoc 5 ngay da duoc ke.");
    goalAchieved(prescribe).

// Lang nghe tin hieu yeu cau khoi tao Scheme phuc hoi tu FaultTolerantSchemeBoard
+recovery_required("reconsult_scheme", FailedGoal, ErrorId, Args) <-
    .print(">>> BAC SI NHAN YEU CAU PHUC HOI: Error = ", ErrorId, " cho Goal = ", FailedGoal);
    .print(">>> Tham so ngu canh loi: ", Args);
    .print(">>> Tien hanh tai kham va danh gia lai tinh trang benh nhan...");
    .wait(1500);
    .print(">>> Tai kham hoan tat: Benh nhan khoi som, giam lieu luong thuoc.");
    
    // Phuc hoi Goal cho benh nhan
    .print(">>> Goi resumeGoal(", FailedGoal, ") de benh nhan tiep tuc lieu trinh!");
    resumeGoal(FailedGoal).
