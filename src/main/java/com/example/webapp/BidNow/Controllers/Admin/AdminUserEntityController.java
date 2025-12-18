package com.example.webapp.BidNow.Controllers.Admin;

import com.example.webapp.BidNow.Dtos.AdminUserEntityDto;
import com.example.webapp.BidNow.Dtos.UserEntityUpdateAdmin;
import com.example.webapp.BidNow.Services.AdminUserEntityService;
import com.example.webapp.BidNow.Services.UserActivityService;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Admin controller for managing users.
 *
 * Base path: /api/admin
 * Endpoints:
 * - list users (paged)
 * - Get user
 * - Get user by username
 * - Edit user.
 *
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserEntityController {


    //todo: An alaksi to app instance na kani refresh to frontend gia na dimiourgounte ksana websocket connections.




    private final AdminUserEntityService adminUserEntityService;

    public AdminUserEntityController(AdminUserEntityService adminUserEntityService) {
        this.adminUserEntityService = adminUserEntityService;
    }


    /**
     * Get users (paginated), with optional search.
     *
     * GET /api/admin/users?page=0&size=20&search=...&searchBy=id
     *
     *
     * @param page     page number (default 0)
     * @param size     page size (default 20)
     * @param search   optional search term
     * @param searchBy optional field to search by (default "id"), Other Options:firebaseId,username
     * @return page of users as AdminUserEntityDto
     */
    @GetMapping(value = "/users")
    public ResponseEntity<Page<AdminUserEntityDto>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "id") String searchBy
    ) {
        return ResponseEntity.ok(
                adminUserEntityService.getUsersPage(page, size, search, searchBy)
        );
    }



    /**
     * Get a user by Firebase ID.
     *
     * GET /api/admin/users/{firebaseId}
     *
     * @param firebaseId firebase user identifier
     * @return user details as AdminUserEntityDto
     */
    @GetMapping(value = "/users/{firebaseId}")
    public ResponseEntity<AdminUserEntityDto> getUser(@PathVariable String firebaseId){
        AdminUserEntityDto adminUserEntityDto = adminUserEntityService.getUser(firebaseId);
        return ResponseEntity.ok(adminUserEntityDto);
    }


    /**
     * Get a user by username.
     *
     * GET /api/admin/users/username/{username}
     *
     * @param username username to search for
     * @return user details as AdminUserEntityDto
     */
    @GetMapping(value = "/users/username/{username}")
    public ResponseEntity<AdminUserEntityDto> getUserByUsername(@PathVariable String username){
        AdminUserEntityDto adminUserEntityDto = adminUserEntityService.getUserByUsername(username);
        return ResponseEntity.ok(adminUserEntityDto);
    }


    /**
     * Update a user by Firebase ID.
     * Phone number cannot be changed!
     * Here Admin can also disable(ban user) and
     * anonymize user in DB and delete in firebase auth DB
     *
     * PUT /api/admin/editUser/{firebaseId}
     *
     * @param firebaseId   firebase user identifier
     * @param userEntityDto update payload (admin fields)
     * @return updated user as AdminUserEntityDto
     */
    @PutMapping(value = "/editUser/{firebaseId}")
    public ResponseEntity<AdminUserEntityDto> editUser(
            @PathVariable String firebaseId,
            @RequestBody UserEntityUpdateAdmin userEntityDto) throws FirebaseAuthException {

        AdminUserEntityDto updatedUser =  adminUserEntityService.updateUser(firebaseId,userEntityDto);

        return ResponseEntity.ok(updatedUser);
    }
}
