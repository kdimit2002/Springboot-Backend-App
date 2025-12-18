//package com.example.webapp.BidNow.Controllers;
//
//import com.example.webapp.BidNow.Dtos.*;
//import com.example.webapp.BidNow.Entities.Category;
//import com.example.webapp.BidNow.Services.AdminService;
//import com.example.webapp.BidNow.Services.AuctionService;
//import com.example.webapp.BidNow.Services.CategoryService;
//import com.example.webapp.BidNow.Services.LocationService;
//import com.google.firebase.auth.FirebaseAuthException;
//import jakarta.validation.Valid;
//import org.springframework.data.domain.Page;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//
//import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;
//
///**
// * @Author Kendeas
// */
//@RestController
//@RequestMapping("/Admin")
//public class AdminController {
//
//
//    private final CategoryService categoryService;
//    private final AuctionService auctionService;
//    private final AdminService adminService;
//    private final LocationService locationService;
//
//    public AdminController(CategoryService categoryService, AuctionService auctionService, AdminService adminService, LocationService locationService) {
//        this.categoryService = categoryService;
//        this.auctionService = auctionService;
//        this.adminService = adminService;
//        this.locationService = locationService;
//    }
//
//    @GetMapping(value = "/users")
//    public ResponseEntity<Page<AdminUserEntityDto>> getUsers(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size
//    ) {
//
//        return ResponseEntity.ok(adminService.getUsersPage(page, size));
//    }
//
//
//
//    @GetMapping(value = "/users/{firebaseId}")
//    public ResponseEntity<AdminUserEntityDto> getUser(@PathVariable String firebaseId){
//        return ResponseEntity.ok(adminService.getUser(firebaseId));
//    }
//
//
//
//
//    @PutMapping(value = "/editUser/{firebaseId}")
//    public ResponseEntity<AdminUserEntityDto> editUser(
//            @PathVariable String firebaseId,
//            @RequestBody UserEntityUpdateAdmin userEntityDto) throws FirebaseAuthException {
//
//        AdminUserEntityDto updatedUser =  adminService.updateUser(firebaseId,userEntityDto);
//
//        return ResponseEntity.ok(updatedUser);
//    }
//
//    ////////////////////   REFERRAL CODE   //////////////////
////
////    @GetMapping(value = "/referralCodes")
////    public ResponseEntity<Page<ReferralCodeDtoAdminResponse>> referralCodes(
////            @RequestParam(defaultValue = "0") int page,
////            @RequestParam(defaultValue = "20") int size
////    ){
////        return ResponseEntity.ok(adminService.getReferralCodes(page, size));
////    }
////
////
////    @PostMapping(value = "/createReferralCode")
////    public ResponseEntity<String> createReferralCode(@RequestBody ReferralCodeRequest referralCodeRequest){
////        adminService.createReferralCode(referralCodeRequest);
////        return ResponseEntity.ok("Referral code has been created");
////    }
////
////    @PatchMapping(value = "/editReferralCode/{id}")
////    public ResponseEntity<ReferralCodeDtoAdminResponse> editReferralCode(
////            @PathVariable Long id,
////            @RequestBody ReferralCodeRequest codeRequest){
////
////        return ResponseEntity.ok(adminService.editReferralCode(id,codeRequest));
////    }
//
//
////    /**
////     * Έγκριση/δημοσίευση δημοπρασίας ΜΟΝΟ από ADMIN.
////     */
////    @PatchMapping("/{id}/approve")
////    //@PreAuthorize("hasRole('ADMIN')")
////    public ResponseEntity<Void> approveAuction(@PathVariable Long id) {
////        auctionService.approveAuction(id);
////        return ResponseEntity.ok().build();
////    }
////
////    /**
////     * Λίστα με όλες τις δημοπρασίες που είναι PENDING_APPROVAL,
////     * για να τις δει ο ADMIN και να τις κάνει approve ή reject.
////     */
////    @GetMapping("/pending")
////    //@PreAuthorize("hasRole('ADMIN')")
////    public ResponseEntity<List<AuctionResponseDto>> getPendingAuctions() {
////        return ResponseEntity.ok(auctionService.getPendingAuctions());
////    }
//
//
//    //////////////////////// CATEGORY ADMIN CONTROLLERS ////////////////////////
////
////
////    // POST δημιουργία κατηγορίας
////    @PostMapping("/createCategory")
////    public ResponseEntity<String> create(@Valid @RequestBody String category) {
////        Category created = categoryService.create(category);
////        return ResponseEntity.ok(created.getName());
////    }
////
////    // PUT update κατηγορίας
////    @PutMapping("/updateCategory/{id}")
////    public ResponseEntity<String > update(@PathVariable Long id,
////                                           @Valid @RequestBody String category) {
////        Category updated = categoryService.update(id, category);
////        return ResponseEntity.ok(updated.getName());
////    }
////
////    // DELETE διαγραφή κατηγορίας
////    @DeleteMapping("/deleteCategory/{id}")
////    public ResponseEntity<Void> delete(@PathVariable Long id) {
////        categoryService.delete(id);
////        return ResponseEntity.noContent().build();
////    }
//}
