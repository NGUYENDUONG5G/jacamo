
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!init.
+!init <-
    .print("Bac si Bob da vao ca lam viec tai phong kham.").


+consultation_request(Patient, Symptoms) <-
    +current_patient(Patient);
    +current_symptoms(Symptoms);
    .print("Tiep nhan benh nhan ", Patient, " voi cac trieu chung: ", Symptoms, ".");
    .print("Tien hanh do than nhiet, nhip tim va soi hong benh nhan...");
    .wait(1000);
    .print("Chan doan xac dinh: Viem hong cap do nhiem khuan. Chi dinh dung khang sinh 5 ngay.");
    .send(Patient, tell, diagnosis_ready(strep_throat)).


+!prescribe[scheme(Sch)] <-
    ?current_patient(Patient);
    Med = "Amoxicillin 500mg";
    Dosage = "2 lan/ngay";
    Duration = "5 ngay";
    +prescribed(Patient, Med, Dosage, Duration);
    .print("Bat dau ke don thuoc dien tu tren scheme ", Sch, "...");
    .wait(1000);
    .print("Don thuoc da ke cho ", Patient, ": ", Med, " (", Dosage, ", lieu trinh ", Duration, ").");
    .send(carol_pharma, tell, prescription(Patient, Med, Dosage, Duration));
    .print("Da chuyen don thuoc sang quay duoc cho duoc si Carol.").


+!review_symptoms[scheme(Sch)] <-
    ?current_patient(Patient);
    .print("Bat dau tai kham va danh gia lai trieu chung benh nhan ", Patient, " tren scheme ", Sch, "...");
    .wait(1000);
    .print("Niem mac hong da giam sung viem 80%, nhung vi khuan tiem an chua duoc tieu diet triet de.").

+!adjust_therapy[scheme(Sch)] <-
    ?current_patient(Patient);
    ?prescribed(Patient, Med, Dosage, Duration);
    .print("Dang xem xet dieu chinh phac do tren scheme ", Sch, "...");
    .wait(1000);
    .print("Chi dinh y khoa: Giu nguyen don ", Med, ". Yeu cau benh nhan uong du ", Duration, " de tranh vi khuan khang thuoc!");
    .send(Patient, tell, doctor_advice("Phai uong du lieu trinh de tranh vi khuan khang thuoc")).






