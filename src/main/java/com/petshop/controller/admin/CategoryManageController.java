
package com.petshop.controller.admin;

import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryManageController {

    private final CategoryRepository categoryRepository;

    // Display all categories
    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin/category-list";
    }

    // Display the form to create a category
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("category", new Category());
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin/category-form";
    }

    // Save a new category
    @PostMapping("/create")
    public String createCategory(
            @ModelAttribute Category category,
            RedirectAttributes redirectAttributes) {

        String code = category.getCode() == null
                ? "" : category.getCode().trim();

        String name = category.getName() == null
                ? "" : category.getName().trim();

        if (code.isEmpty() || name.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code and name are required."
            );
            return "redirect:/admin/categories/create";
        }

        if (categoryRepository.existsByCode(code)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code already exists."
            );
            return "redirect:/admin/categories/create";
        }

        category.setCode(code);
        category.setName(name);

        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        if (category.getIsActive() == null) {
            category.setIsActive(true);
        }

        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category created successfully."
        );

        return "redirect:/admin/categories";
    }

    // Display the form to edit a category
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Integer id,
            Model model,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        model.addAttribute("category", optionalCategory.get());
        model.addAttribute("categories", categoryRepository.findAll());

        return "admin/category-form";
    }

    // Update an existing category
    @PostMapping("/update/{id}")
    public String updateCategory(
            @PathVariable Integer id,
            @ModelAttribute Category formCategory,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        String code = formCategory.getCode() == null
                ? "" : formCategory.getCode().trim();

        String name = formCategory.getName() == null
                ? "" : formCategory.getName().trim();

        if (code.isEmpty() || name.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code and name are required."
            );
            return "redirect:/admin/categories/edit/" + id;
        }

        Optional<Category> duplicate =
                categoryRepository.findByCode(code);

        if (duplicate.isPresent()
                && !duplicate.get().getId().equals(id)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code already exists."
            );
            return "redirect:/admin/categories/edit/" + id;
        }

        Category category = optionalCategory.get();

        category.setCode(code);
        category.setName(name);
        category.setDescription(formCategory.getDescription());
        category.setSortOrder(
                formCategory.getSortOrder() == null
                        ? 0 : formCategory.getSortOrder()
        );
        category.setParentCategory(formCategory.getParentCategory());

        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category updated successfully."
        );

        return "redirect:/admin/categories";
    }

    // Activate or deactivate a category
    @PostMapping("/toggle-status/{id}")
    public String toggleCategoryStatus(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        Category category = optionalCategory.get();

        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));

        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category status updated successfully."
        );

        return "redirect:/admin/categories";
    }
}
