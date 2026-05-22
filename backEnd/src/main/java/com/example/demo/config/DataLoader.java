package com.example.demo.config;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(1)
public class DataLoader implements CommandLineRunner {

    private final InvoiceRepo InvoiceRepo;
    private final UserRepo userRepo;
    private final WorkOrderRepo workOrderRepo;
    private final PaymentRepo paymentRepo;
    private final CostRepo costRepo;
    private final TenantRepo tenantRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.reset-seed-passwords:false}")
    private boolean resetSeedPasswords;

    public DataLoader(InvoiceRepo InvoiceRepo, UserRepo userRepo, WorkOrderRepo workOrderRepo,
                      PaymentRepo paymentRepo, CostRepo costRepo,
                      TenantRepo tenantRepo,
                      PasswordEncoder passwordEncoder) {
        this.InvoiceRepo = InvoiceRepo;
        this.userRepo = userRepo;
        this.workOrderRepo = workOrderRepo;
        this.paymentRepo = paymentRepo;
        this.costRepo = costRepo;
        this.tenantRepo = tenantRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        userRepo.normalizeClientRoles();
        userRepo.deleteViewerUsers();

        Tenant defaultTenant = ensureTenant("Demo Distribuidora", "distribuidora-demo");
        ensureUser("superadmin", "superadmin123", AppUserRole.SUPER_ADMIN, "1140000000", defaultTenant);
        AppUser admin  = ensureUser("admin",  "admin123",  AppUserRole.ADMIN,  "1140000001", defaultTenant);
        AppUser carlos = ensureUser("carlos", "gestor123", AppUserRole.GESTOR, "1140000002", defaultTenant);
        AppUser laura  = ensureUser("laura",  "gestor123", AppUserRole.GESTOR, "1140000003", defaultTenant);

        if (!seedDemoData) return;
        if (InvoiceRepo.count() > 0) return;

        seedOrders(admin, carlos, laura);
        seedCosts();
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    private Tenant ensureTenant(String name, String slug) {
        return tenantRepo.findBySlug(slug).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setName(name);
            tenant.setSlug(slug);
            tenant.setCreatedAt(LocalDateTime.now());
            return tenantRepo.save(tenant);
        });
    }

    private AppUser ensureUser(String username, String rawPassword, AppUserRole role, String phone, Tenant tenant) {
        AppUser user = userRepo.findByUsername(username).orElseGet(() -> {
            AppUser u = new AppUser(username, passwordEncoder.encode(rawPassword), role, phone);
            u.setTenant(tenant);
            return userRepo.save(u);
        });
        // Never overwrite an existing password — only update role/phone/tenant so
        // passwords changed via the admin panel survive restarts
        if (resetSeedPasswords) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
        user.setAppUserRole(role);
        user.setPhoneNumber(phone);
        user.setTenant(tenant);
        return userRepo.save(user);
    }

    // ─── Orders ──────────────────────────────────────────────────────────────

    private void seedOrders(AppUser admin, AppUser carlos, AppUser laura) {
        // ── February (orders 1–12, all CERRADO / ABONADO) ────────────────────
        order("Factura #001 - Electrodomésticos línea blanca", 1, d("2026-02-20"), d("2026-03-13"), d("2026-03-10"), 320_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "gonzalez@gmail.com", "Gonzalez Ricardo",
            List.of(pay(d("2026-02-20"), "DEPOSIT", 128_000, "BANK_TRANSFER"),
                    pay(d("2026-03-12"), "RESTO",   192_000, "CASH")));

        order("Factura #002 - Artículos de limpieza x4 lotes", 4, d("2026-02-21"), d("2026-03-07"), d("2026-03-05"), 180_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "martinez@gmail.com", "Martínez Ana",
            List.of(pay(d("2026-02-21"), "DEPOSIT",  72_000, "CASH"),
                    pay(d("2026-03-06"), "RESTO",   108_000, "CASH")));

        order("Factura #003 - Insumos de oficina",              1, d("2026-02-22"), d("2026-03-15"), d("2026-03-12"), 290_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "lopez@hotmail.com", "López Carla",
            List.of(pay(d("2026-02-22"), "DEPOSIT", 116_000, "BANK_TRANSFER"),
                    pay(d("2026-03-14"), "RESTO",   174_000, "BANK_TRANSFER")));

        order("Factura #004 - Tecnología y accesorios",         1, d("2026-02-24"), d("2026-03-10"), d("2026-03-08"), 195_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "fernandez@gmail.com", "Fernández José",
            List.of(pay(d("2026-02-24"), "DEPOSIT",  78_000, "CREDIT_DEBIT_CARD"),
                    pay(d("2026-03-09"), "RESTO",   117_000, "BANK_TRANSFER")));

        order("Factura #005 - Textiles y ropa de trabajo",      1, d("2026-02-25"), d("2026-03-20"), d("2026-03-18"), 420_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "garcia@gmail.com", "García Pedro",
            List.of(pay(d("2026-02-25"), "DEPOSIT", 168_000, "BANK_TRANSFER"),
                    pay(d("2026-03-17"), "RESTO",   252_000, "BANK_TRANSFER")));

        order("Factura #006 - Herramientas eléctricas",         1, d("2026-02-26"), d("2026-03-12"), d("2026-03-10"), 120_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "rodriguez@gmail.com", "Rodríguez Marta",
            List.of(pay(d("2026-02-26"), "DEPOSIT",  48_000, "CASH"),
                    pay(d("2026-03-10"), "RESTO",    72_000, "CASH")));

        order("Factura #007 - Equipamiento de cocina industrial",1, d("2026-02-27"), d("2026-03-25"), d("2026-03-22"), 480_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "sanchez@gmail.com", "Sánchez Laura",
            List.of(pay(d("2026-02-27"), "DEPOSIT", 192_000, "BANK_TRANSFER"),
                    pay(d("2026-03-22"), "RESTO",   288_000, "BANK_TRANSFER")));

        order("Factura #008 - Materiales de construcción x2",   2, d("2026-02-28"), d("2026-03-10"), d("2026-03-08"), 160_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "perez@gmail.com", "Pérez Fernando",
            List.of(pay(d("2026-02-28"), "DEPOSIT",  64_000, "CASH"),
                    pay(d("2026-03-09"), "RESTO",    96_000, "CASH")));

        order("Factura #009 - Insumos médicos y descartables",  1, d("2026-03-01"), d("2026-03-20"), d("2026-03-18"), 220_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "diaz@gmail.com", "Díaz Valentina",
            List.of(pay(d("2026-03-01"), "DEPOSIT",  88_000, "BANK_TRANSFER"),
                    pay(d("2026-03-17"), "RESTO",   132_000, "CREDIT_DEBIT_CARD")));

        order("Factura #010 - Artículos de ferretería",         1, d("2026-03-02"), d("2026-03-22"), d("2026-03-20"), 260_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "gomez@gmail.com", "Gómez Sofía",
            List.of(pay(d("2026-03-02"), "DEPOSIT", 104_000, "CASH"),
                    pay(d("2026-03-20"), "RESTO",   156_000, "CASH")));

        order("Factura #011 - Papelería y consumibles",         1, d("2026-03-03"), d("2026-03-28"), d("2026-03-26"), 580_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "torres@gmail.com", "Torres Marcos",
            List.of(pay(d("2026-03-03"), "DEPOSIT", 232_000, "BANK_TRANSFER"),
                    pay(d("2026-03-08"), "EXTRA",    50_000, "CASH"),
                    pay(d("2026-03-25"), "RESTO",   298_000, "BANK_TRANSFER")));

        order("Factura #012 - Productos de higiene industrial",  1, d("2026-03-04"), d("2026-03-28"), d("2026-03-26"), 490_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "alvarez@gmail.com", "Álvarez Diego",
            List.of(pay(d("2026-03-04"), "DEPOSIT", 196_000, "BANK_TRANSFER"),
                    pay(d("2026-03-26"), "RESTO",   294_000, "BANK_TRANSFER")));

        // ── March batch 1 (orders 13–25, CERRADO) ────────────────────────────
        order("Factura #013 - Repuestos automotrices",          1, d("2026-03-05"), d("2026-03-26"), d("2026-03-24"), 195_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "morales@gmail.com", "Morales Cecilia",
            List.of(pay(d("2026-03-05"), "DEPOSIT",  78_000, "CREDIT_DEBIT_CARD"),
                    pay(d("2026-03-24"), "RESTO",   117_000, "BANK_TRANSFER")));

        order("Factura #014 - Bebidas sin alcohol x2 pallets",  2, d("2026-03-06"), d("2026-03-24"), d("2026-03-22"), 140_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "romero@gmail.com", "Romero Patricia",
            List.of(pay(d("2026-03-06"), "DEPOSIT",  56_000, "CASH"),
                    pay(d("2026-03-22"), "RESTO",    84_000, "CASH")));

        order("Factura #015 - Alimentos secos y enlatados x3",  3, d("2026-03-07"), d("2026-03-28"), d("2026-03-26"), 270_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "castro@gmail.com", "Castro Hernán",
            List.of(pay(d("2026-03-07"), "DEPOSIT", 108_000, "BANK_TRANSFER"),
                    pay(d("2026-03-26"), "RESTO",   162_000, "BANK_TRANSFER")));

        order("Factura #016 - Productos de librería mayorista", 1, d("2026-03-08"), d("2026-04-01"), d("2026-03-30"), 310_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "gutierrez@gmail.com", "Gutiérrez Elena",
            List.of(pay(d("2026-03-08"), "DEPOSIT", 124_000, "CASH"),
                    pay(d("2026-03-30"), "RESTO",   186_000, "CASH")));

        order("Factura #017 - Indumentaria laboral",            1, d("2026-03-10"), d("2026-03-31"), d("2026-03-29"), 185_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "mendez@gmail.com", "Méndez Tomás",
            List.of(pay(d("2026-03-10"), "DEPOSIT",  74_000, "BANK_TRANSFER"),
                    pay(d("2026-03-28"), "RESTO",   111_000, "BANK_TRANSFER")));

        order("Factura #018 - Señalética y cartelería",         1, d("2026-03-11"), d("2026-03-28"), d("2026-03-26"),  95_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "ramos@gmail.com", "Ramos Luciana",
            List.of(pay(d("2026-03-11"), "DEPOSIT",  38_000, "CASH"),
                    pay(d("2026-03-26"), "RESTO",    57_000, "CASH")));

        order("Factura #019 - Equipos de seguridad e higiene",  1, d("2026-03-12"), d("2026-04-08"), d("2026-04-05"), 650_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "ruiz@gmail.com", "Ruiz Santiago",
            List.of(pay(d("2026-03-12"), "DEPOSIT", 260_000, "BANK_TRANSFER"),
                    pay(d("2026-04-01"), "EXTRA",    50_000, "BANK_TRANSFER"),
                    pay(d("2026-04-05"), "RESTO",   340_000, "BANK_TRANSFER")));

        order("Factura #020 - Insumos gastronómicos",           1, d("2026-03-13"), d("2026-04-03"), d("2026-04-01"), 280_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "herrera@gmail.com", "Herrera Claudia",
            List.of(pay(d("2026-03-13"), "DEPOSIT", 112_000, "CASH"),
                    pay(d("2026-04-01"), "RESTO",   168_000, "CASH")));

        order("Factura #021 - Químicos y lubricantes",          1, d("2026-03-14"), d("2026-04-10"), d("2026-04-08"), 390_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "medina@gmail.com", "Medina Roxana",
            List.of(pay(d("2026-03-14"), "DEPOSIT", 156_000, "BANK_TRANSFER"),
                    pay(d("2026-04-08"), "RESTO",   234_000, "BANK_TRANSFER")));

        order("Factura #022 - Artículos deportivos x2",         2, d("2026-03-15"), d("2026-04-12"), d("2026-04-10"), 720_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "luna@gmail.com", "Luna Fabián",
            List.of(pay(d("2026-03-15"), "DEPOSIT", 288_000, "BANK_TRANSFER"),
                    pay(d("2026-04-10"), "RESTO",   432_000, "BANK_TRANSFER")));

        order("Factura #023 - Suministros de impresión",        1, d("2026-03-17"), d("2026-04-05"), d("2026-04-03"), 145_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "vargas@gmail.com", "Vargas Miriam",
            List.of(pay(d("2026-03-17"), "DEPOSIT",  58_000, "CASH"),
                    pay(d("2026-04-03"), "RESTO",    87_000, "CASH")));

        order("Factura #024 - Materiales eléctricos",           1, d("2026-03-18"), d("2026-04-07"), d("2026-04-05"), 235_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "silva@gmail.com", "Silva Gastón",
            List.of(pay(d("2026-03-18"), "DEPOSIT",  94_000, "BANK_TRANSFER"),
                    pay(d("2026-04-05"), "RESTO",   141_000, "BANK_TRANSFER")));

        order("Factura #025 - Productos de panadería industrial",1, d("2026-03-19"), d("2026-04-05"), d("2026-04-03"), 175_000, PaymentStatus.ABONADO, Status.CERRADO, carlos, "aguilar@gmail.com", "Aguilar Jimena",
            List.of(pay(d("2026-03-19"), "DEPOSIT",  70_000, "CASH"),
                    pay(d("2026-04-03"), "RESTO",   105_000, "CASH")));

        // ── March batch 2 (orders 26–35, EN_GESTION) ─────────────────────────
        order("Factura #026 - Equipamiento para oficina",       1, d("2026-03-20"), d("2026-04-20"), d("2026-04-18"), 890_000, PaymentStatus.PAGO_SEÑA, Status.CERRADO, laura, "ortiz@gmail.com", "Ortiz Empresa SRL",
            List.of(pay(d("2026-03-20"), "DEPOSIT", 356_000, "BANK_TRANSFER")));

        order("Factura #027 - Descartables y packaging x3",     3, d("2026-03-21"), d("2026-04-12"), d("2026-04-10"), 160_000, PaymentStatus.PAGO_SEÑA, Status.CERRADO, carlos, "molina@gmail.com", "Molina Beatriz",
            List.of(pay(d("2026-03-21"), "DEPOSIT",  64_000, "CASH")));

        order("Factura #028 - Cosmética y perfumería",          1, d("2026-03-22"), d("2026-04-15"), d("2026-04-13"), 320_000, PaymentStatus.PAGO_SEÑA, Status.CERRADO, laura, "moreno@gmail.com", "Moreno Adriana",
            List.of(pay(d("2026-03-22"), "DEPOSIT", 128_000, "BANK_TRANSFER")));

        order("Factura #029 - Juguetes y entretenimiento x6",   6, d("2026-03-24"), d("2026-04-18"), d("2026-04-16"), 420_000, PaymentStatus.PAGO_SEÑA, Status.CERRADO, carlos, "flores@gmail.com", "Flores Nicolás",
            List.of(pay(d("2026-03-24"), "DEPOSIT", 168_000, "CASH")));

        order("Factura #030 - Frutas y verduras orgánicas",     1, d("2026-03-25"), d("2026-04-14"), d("2026-04-12"), 195_000, PaymentStatus.ABONADO, Status.CERRADO, laura, "cruz@gmail.com", "Cruz Alejandra",
            List.of(pay(d("2026-03-25"), "DEPOSIT",  78_000, "CREDIT_DEBIT_CARD"),
                    pay(d("2026-04-12"), "RESTO",   117_000, "BANK_TRANSFER")));

        order("Factura #031 - Maquinaria de taller",            1, d("2026-03-26"), d("2026-04-22"), d("2026-04-20"), 650_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "reyes@gmail.com", "Reyes Gabriel",
            List.of(pay(d("2026-03-26"), "DEPOSIT", 260_000, "BANK_TRANSFER")));

        order("Factura #032 - Insumos plásticos varios",        1, d("2026-03-27"), d("2026-04-24"), d("2026-04-22"), 480_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "santos@gmail.com", "Santos Valeria",
            List.of(pay(d("2026-03-27"), "DEPOSIT", 192_000, "BANK_TRANSFER")));

        order("Factura #033 - Calzado industrial x4 pares",     4, d("2026-03-28"), d("2026-04-20"), d("2026-04-18"), 360_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "rivera@gmail.com", "Rivera Carlos",
            List.of(pay(d("2026-03-28"), "DEPOSIT", 144_000, "CASH")));

        order("Factura #034 - Productos lácteos refrigerados",  1, d("2026-03-29"), d("2026-04-18"), d("2026-04-16"), 135_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "ramirez@gmail.com", "Ramírez Florencia",
            List.of(pay(d("2026-03-29"), "DEPOSIT",  54_000, "BANK_TRANSFER")));

        order("Factura #035 - Artículos de ferretería pesada",  1, d("2026-03-30"), d("2026-04-25"), d("2026-04-23"), 410_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "rojas@gmail.com", "Rojas Inés",
            List.of(pay(d("2026-03-30"), "DEPOSIT", 164_000, "CASH")));

        // ── April (orders 36–45, EN_GESTION) ─────────────────────────────────
        order("Factura #036 - Equipos de refrigeración",        1, d("2026-04-01"), d("2026-04-28"), d("2026-04-26"), 380_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "nunez@gmail.com", "Núñez Esteban",
            List.of(pay(d("2026-04-01"), "DEPOSIT", 152_000, "BANK_TRANSFER")));

        order("Factura #037 - Suplementos alimenticios",        1, d("2026-04-02"), d("2026-05-02"), d("2026-04-30"), 520_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "vega@gmail.com", "Vega Martín",
            List.of(pay(d("2026-04-02"), "DEPOSIT", 208_000, "BANK_TRANSFER")));

        order("Factura #038 - Productos de vidrio y cristal",   1, d("2026-04-03"), d("2026-05-05"), d("2026-05-03"), 790_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "mora@gmail.com", "Mora Daniela",
            List.of(pay(d("2026-04-03"), "DEPOSIT", 316_000, "CREDIT_DEBIT_CARD")));

        order("Factura #039 - Mobiliario escolar",              1, d("2026-04-04"), d("2026-05-02"), d("2026-04-30"), 490_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "jimenez@gmail.com", "Jiménez Roberto",
            List.of(pay(d("2026-04-04"), "DEPOSIT", 196_000, "BANK_TRANSFER")));

        order("Factura #040 - Insumos veterinarios",            1, d("2026-04-06"), d("2026-04-28"), d("2026-04-26"), 280_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "guerrero@gmail.com", "Guerrero Liliana",
            List.of(pay(d("2026-04-06"), "DEPOSIT", 112_000, "CASH")));

        order("Factura #041 - Equipos de audio y video",        1, d("2026-04-07"), d("2026-04-28"), d("2026-04-26"), 185_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "sosa@gmail.com", "Sosa Mariela",
            List.of(pay(d("2026-04-07"), "DEPOSIT",  74_000, "BANK_TRANSFER")));

        order("Factura #042 - Artículos de iluminación",        1, d("2026-04-08"), d("2026-05-05"), d("2026-05-03"), 220_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "carrillo@gmail.com", "Carrillo Horacio",
            List.of(pay(d("2026-04-08"), "DEPOSIT",  88_000, "BANK_TRANSFER")));

        order("Factura #043 - Uniformes y ropa corporativa",    1, d("2026-04-10"), d("2026-05-08"), d("2026-05-06"), 340_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "mendoza@gmail.com", "Mendoza Patricia",
            List.of(pay(d("2026-04-10"), "DEPOSIT", 136_000, "CASH")));

        order("Factura #044 - Equipos de medición y control",   4, d("2026-04-12"), d("2026-05-10"), d("2026-05-08"), 520_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, laura, "ibarra@gmail.com", "Ibarra Cristina",
            List.of(pay(d("2026-04-12"), "DEPOSIT", 208_000, "BANK_TRANSFER")));

        order("Factura #045 - Suministros de limpieza industrial",1, d("2026-04-14"), d("2026-05-12"), d("2026-05-10"), 310_000, PaymentStatus.PAGO_SEÑA, Status.EN_GESTION, carlos, "cabrera@gmail.com", "Cabrera Ignacio",
            List.of(pay(d("2026-04-14"), "DEPOSIT", 124_000, "CREDIT_DEBIT_CARD")));
    }

    private void order(String titulo, long cantidad,
                       LocalDate start, LocalDate fechaEstimada, LocalDate fechaEntrega,
                       int precio, PaymentStatus pagoStatus, Status woStatus,
                       AppUser owner, String email, String notas,
                       List<PaymentRecord> payments) {

        Invoice p = new Invoice();
        p.setTitulo(titulo);
        p.setCantidad(cantidad);
        p.setStartDate(start);
        p.setFechaEstimada(fechaEstimada);
        p.setFechaEntrega(fechaEntrega);
        p.setPrecio(new BigDecimal(precio));
        p.setPagoStatus(pagoStatus);
        p.setOwner(owner);
        p.setTenant(owner.getTenant());
        p.setClientPhone(email);
        p.setNotas(notas);
        p = InvoiceRepo.save(p);

        WorkOrder wo = new WorkOrder();
        wo.setInvoice(p);
        wo.setStatus(woStatus);
        wo.setUpdateAt(start.atStartOfDay().plusHours(9));
        workOrderRepo.save(wo);

        for (PaymentRecord pr : payments) {
            OrderPayments op = new OrderPayments();
            op.setInvoice(p);
            op.setTenant(p.getTenant());
            op.setPaymentDate(pr.date());
            op.setPaymentType(pr.type());
            op.setAmount(new BigDecimal(pr.amount()));
            op.setPaymentMethod(pr.method());
            paymentRepo.save(op);
        }
    }

    private PaymentRecord pay(LocalDate date, String type, int amount, String method) {
        return new PaymentRecord(date, type, amount, method);
    }

    private record PaymentRecord(LocalDate date, String type, int amount, String method) {}

    // ─── Costs ───────────────────────────────────────────────────────────────

    private void seedCosts() {
        // February costs
        cost(CostType.RENT,     d("2026-02-01"), 250_000, PaymentFrequency.MONTHLY,  "Alquiler depósito febrero");
        cost(CostType.SALARY,   d("2026-02-28"), 480_000, PaymentFrequency.MONTHLY,  "Sueldo administrativo Juan");
        cost(CostType.SALARY,   d("2026-02-28"), 350_000, PaymentFrequency.MONTHLY,  "Sueldo asistente Pablo");
        cost(CostType.SERVICES, d("2026-02-10"),  42_500, PaymentFrequency.MONTHLY,  "Luz y gas depósito");
        cost(CostType.SERVICES, d("2026-02-12"),   8_900, PaymentFrequency.MONTHLY,  "Internet fibra óptica");
        cost(CostType.ADS,      d("2026-02-20"),  25_000, PaymentFrequency.MONTHLY,  "Publicidad digital");
        cost(CostType.TAX,      d("2026-02-22"),  68_000, PaymentFrequency.MONTHLY,  "Monotributo AFIP febrero");

        // March costs
        cost(CostType.RENT,     d("2026-03-01"), 250_000, PaymentFrequency.MONTHLY,  "Alquiler depósito marzo");
        cost(CostType.SALARY,   d("2026-03-31"), 480_000, PaymentFrequency.MONTHLY,  "Sueldo administrativo Juan");
        cost(CostType.SALARY,   d("2026-03-31"), 350_000, PaymentFrequency.MONTHLY,  "Sueldo asistente Pablo");
        cost(CostType.SERVICES, d("2026-03-10"),  44_800, PaymentFrequency.MONTHLY,  "Luz y gas depósito");
        cost(CostType.SERVICES, d("2026-03-12"),   8_900, PaymentFrequency.MONTHLY,  "Internet fibra óptica");
        cost(CostType.ADS,      d("2026-03-20"),  30_000, PaymentFrequency.MONTHLY,  "Publicidad digital");
        cost(CostType.TAX,      d("2026-03-22"),  68_000, PaymentFrequency.MONTHLY,  "Monotributo AFIP marzo");
        cost(CostType.OTHERS,   d("2026-03-25"),  18_500, PaymentFrequency.ONE_TIME, "Reparación vehículo de reparto");

        // April costs (partial month)
        cost(CostType.RENT,     d("2026-04-01"), 250_000, PaymentFrequency.MONTHLY,  "Alquiler depósito abril");
        cost(CostType.SALARY,   d("2026-04-15"), 480_000, PaymentFrequency.MONTHLY,  "Sueldo administrativo Juan");
        cost(CostType.SALARY,   d("2026-04-15"), 350_000, PaymentFrequency.MONTHLY,  "Sueldo asistente Pablo");
        cost(CostType.SERVICES, d("2026-04-10"),  46_200, PaymentFrequency.MONTHLY,  "Luz y gas depósito");
    }

    private void cost(CostType type, LocalDate date, int amount, PaymentFrequency freq, String reason) {
        Costs c = new Costs();
        c.setCostType(type);
        c.setDate(date);
        c.setAmount(new BigDecimal(amount));
        c.setFrequency(freq);
        c.setReason(reason);
        c.setCreatedAt(date.atStartOfDay().plusHours(10));
        costRepo.save(c);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }
}
