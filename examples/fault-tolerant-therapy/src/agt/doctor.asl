
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!init.
+!init <-
    .print("Bac si Bob da vao ca lam viec tai phong kham.").


+consultation_request(Patient, Symptoms) <-
    +current_patient(Patient);
    +current_symptoms(Symptoms);
    .print("[BAC SI BOB] Tiep nhan benh nhan ", Patient, " voi cac trieu chung: ", Symptoms, ".");
    .print("[BAC SI BOB] Tien hanh do than nhiet, nhip tim va soi hong benh nhan...");
    .wait(1000);
    .print("[BAC SI BOB] Chan doan xac dinh: Viem hong cap do nhiem khuan. Chi dinh dung khang sinh 5 ngay.");
    .send(Patient, tell, diagnosis_ready(strep_throat)).


+!prescribe[scheme(Sch)] <-
    ?current_patient(Patient);
    Med = "Amoxicillin 500mg";
    Dosage = "2 lan/ngay";
    Duration = "5 ngay";
    +prescribed(Patient, Med, Dosage, Duration);
    .print("[BAC SI BOB] Bat dau ke don thuoc dien tu tren scheme ", Sch, "...");
    .wait(1000);
    .print("[BAC SI BOB] Don thuoc da ke cho ", Patient, ": ", Med, " (", Dosage, ", lieu trinh ", Duration, ").");
    .send(carol_pharma, tell, prescription(Patient, Med, Dosage, Duration));
    .print("[BAC SI BOB] Da chuyen don thuoc sang quay duoc cho duoc si Carol.").


+!review_symptoms[scheme(Sch)] <-
    ?current_patient(Patient);
    .print("[BAC SI BOB] [PHUC HOI] Bat dau tai kham va danh gia lai trieu chung benh nhan ", Patient, " tren scheme ", Sch, "...");
    .wait(1000);
    .print("[BAC SI BOB] [PHUC HOI] Niem mac hong da giam sung viem 80%, nhung vi khuan tiem an chua duoc tieu diet triet de.").

+!adjust_therapy[scheme(Sch)] <-
    ?current_patient(Patient);
    ?prescribed(Patient, Med, Dosage, Duration);
    .print("[BAC SI BOB] [PHUC HOI] Dang xem xet dieu chinh phac do tren scheme ", Sch, "...");
    .wait(1000);
    .print("[BAC SI BOB] [PHUC HOI] Chi dinh y khoa: Giu nguyen don ", Med, ". Yeu cau benh nhan uong du ", Duration, " de tranh vi khuan khang thuoc!").


+recovery_required(RecSch, Goal, Error, Args) <-
    .print("[NGOAI LE] Nhan tin hieu yeu cau phuc hoi '", RecSch, "' cho muc tieu '", Goal, "' do loi '", Error, "'!");
    .print("[NGOAI LE] Cac tham so thu thap duoc: ", Args);
    !handle_reconsult(RecSch, Goal, Args).

+!handle_reconsult(RecSch, Goal, [Patient, Day, Reason]) <-
    .print("[BAC SI BOB] [CANH BAO NGUY HIEM] Benh nhan ", Patient, " het trieu chung som vao ngay ", Day, " (ly do: ", Reason, ") va co y dinh ngung uong thuoc!");
    .print("[BAC SI BOB] [CANH BAO NGUY HIEM] Dung khang sinh som dan den nguy co tai phat nang va hinh thanh vi khuan khang thuoc (Antibiotic Resistance)!");
    .print("[BAC SI BOB] Tien hanh quy trinh tai kham khan cap theo recovery scheme '", RecSch, "'...");
    !review_symptoms[scheme(RecSch)];
    !adjust_therapy[scheme(RecSch)];
    .send(Patient, tell, doctor_advice("Phai uong du lieu trinh de tranh vi khuan khang thuoc"));
    .wait(500);
    if (lookupArtifact("therapy_sch", SchId)) {
        recoveryFinished(RecSch)[artifact_id(SchId)];
    } else {
        recoveryFinished(RecSch);
    };
    .print("[BAC SI BOB] Da hoan tat quy trinh phuc hoi '", RecSch, "', he thong da mo khoa (resumed) lieu trinh thanh cong.").


+recoverySchemeRequired(RecSch, Goal) <-
    .print("[NGOAI LE] SchemeBoard thong bao can phuc hoi scheme '", RecSch, "' cho goal '", Goal, "'.").

+recovery_succeeded(RecSch, Goal) <-
    .print("[PHUC HOI] SchemeBoard thong bao recovery '", RecSch, "' da xu ly xong cho goal '", Goal, "'!").


