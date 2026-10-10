// Agent Doctor
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

// Thuc hien muc tieu ke don thuoc (prescribe)
+!prescribe[scheme(Sch)] <-
    .wait(1000).

// Thuc hien cac muc tieu trong quy trinh phuc hoi
+!review_symptoms[scheme(Sch)] <-
    .wait(1000).

+!adjust_therapy[scheme(Sch)] <-
    .wait(1000).
