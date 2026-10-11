
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!init.
+!init <-
    .print("Duoc si Carol da san sang phuc vu tai quay thuoc.").


+prescription(Patient, Med, Dosage, Duration) <-
    .print("[DUOC SI CAROL] Da tiep nhan don thuoc dien tu cho benh nhan ", Patient, ": ", Med, " (", Dosage, ", ", Duration, ").").


+!fill_prescription[scheme(Sch)] <-
    .print("[DUOC SI CAROL] Tiep nhan yeu cau cap phat thuoc tren scheme ", Sch, ".");
    ?prescription(Patient, Med, Dosage, Duration);
    .print("[DUOC SI CAROL] Kiem tra don thuoc, han su dung va tien hanh boc thuoc ", Med, "...");
    .wait(1000);
    .print("[DUOC SI CAROL] Da boc thuoc xong va ban giao cho benh nhan ", Patient, ".");
    .print("[DUOC SI CAROL] Dan do benh nhan ", Patient, ": 'Thuoc ", Med, " voi lieu ", Dosage, " phai uong du ", Duration, " theo don, tuyet doi khong duoc bo ngang du da thay het trieu chung!'");
    .send(Patient, tell, medicine_dispensed(Med, Dosage, Duration)).



