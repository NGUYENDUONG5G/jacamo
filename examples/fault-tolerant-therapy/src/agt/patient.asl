
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!init.
+!init <-
    .print("Benh nhan Alice da den benh vien de tham kham.").


early_recovery_reason("no_more_symptoms").


+!consult[scheme(Sch)] <-
    .print("Bat dau kham benh voi bac si Bob (scheme: ", Sch, ")...");
    .print("Trinh bay trieu chung voi bac si: Sot cao 39 do C va dau rat hong suot 2 ngay nay.");
    .send(dr_bob, tell, consultation_request(alice_patient, [fever_39, sore_throat]));
    .wait(1500);
    .print("Da duoc bac si tham kham xong. Dang cho bac si ke don va duoc si cap thuoc.").


+medicine_dispensed(Med, Dosage, Duration) <-
    +my_prescription(Med, Dosage, Duration);
    .print("Da nhan thuoc tu quay duoc: ", Med, " (", Dosage, ", ", Duration, ").").
+!follow_therapy[scheme(Sch)] <-
    ?my_prescription(Med, Dosage, Duration);
    .print("Bat dau lieu trinh uong thuoc ", Med, " (", Dosage, ") trong ", Duration, " (scheme: ", Sch, ")...");
    .wait(1000);
    .print("Ngay 1 - 2: Uong thuoc deu dan ", Dosage, " theo chi dinh. Con sot da ha.");
    .wait(1000);
    .print("Ngay 3: Thay het sot hoan toan va hong khong con dau! Alice dinh dung thuoc som.");
    .print("Phat sinh niem tin noi tai: symptoms_cleared(alice_patient, 3)...");

    +symptoms_cleared(alice_patient, 3);
    
    .print("Da nhan duoc huong dan va loi khuyen tu bac si Bob sau buoi tai kham!");
    .print("Toi da hieu vi khuan co the van con tiem an. Toi se nghiem tuc uong not cac ngay con lai de tranh khang thuoc.");
    .wait(1000);
    .print("Ngay 4: Tiep tuc uong ", Med, " dung gio va du lieu.");
    .wait(1000);
    .print("Ngay 5: Hoan tat tron ven lieu trinh ", Duration, "! Benh khoi triet de va an toan.").




