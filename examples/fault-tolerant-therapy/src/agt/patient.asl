// Agent Patient
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!start.

+!start <-
    .print("Benh nhan Alice da san sang.").

// Thuc hien muc tieu kham benh (consult)
+!consult[scheme(Sch)] <-
    .print("Benh nhan Alice dang kham benh voi bac si...");
    .wait(1000);
    .print("Kham benh hoan tat.");
    // Thong bao hoan thanh goal
    goalAchieved(consult).

// Thuc hien muc tieu theo doi lieu trinh (follow_therapy)
+!follow_therapy[scheme(Sch)] <-
    .print("Bat dau lieu trinh uong thuoc 5 ngay.");
    .wait(1000);
    .print("Ngay 1: Da uong thuoc.");
    .wait(1000);
    .print("Ngay 2: Da uong thuoc.");
    .wait(1000);
    .print("Ngay 3: Trieu chung da bien mat som (Day 3 < 5)!");
    
    // Chia se belief len Board de kich hoat phat hien ngoai le
    .print("Chia se du kien: symptoms_cleared(alice, 3) len Board...");
    updateOrgBelief("symptoms_cleared(alice, 3)").

// Lang nghe khi Goal bi tam dung do ngoai le
+goalSuspended("follow_therapy", ErrorId) <-
    .print(">>> NHAN THONG BAO: Lieu trinh follow_therapy bi TAM DUNG (suspended) do ngoai le: ", ErrorId);
    .print(">>> Tam ngung uong thuoc, cho bac si tai kham...").

// Lang nghe khi Goal duoc phuc hoi sau khi tai kham
+goalResumed("follow_therapy") <-
    .print(">>> NHAN THONG BAO: Lieu trinh follow_therapy da duoc PHUC HOI (resumed)!");
    .print(">>> Tiep tuc uong thuoc theo huong dan dieu chinh cua bac si.");
    .wait(1000);
    .print("Hoan tat toan bo lieu trinh dieu tri!");
    goalAchieved(follow_therapy).
