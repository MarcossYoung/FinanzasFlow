# Codex Instructions — FinanzasFlow Finance Panel

## Project context
FinanzasFlow is a B2B invoice + collections workspace. Spring Boot backend (Java 17),
React frontend at `frontEnd/workflow/src/`. Another agent (Claude) is simultaneously
working on other files — **do not touch files outside your ownership list below.**

## Your files (only edit these)
```
backEnd/src/main/java/com/example/demo/service/FinanceService.java
backEnd/src/main/java/com/example/demo/dto/FinanceDashboardResponse.java
frontEnd/workflow/src/views/finances.jsx
frontEnd/workflow/src/components/comaprisonBarChart.jsx
frontEnd/workflow/src/components/FinanceReportPdf.jsx   ← NEW FILE
```

**Do NOT touch:** CostController.java, CostRepo.java, CostAutomationService.java,
CostsManager.jsx, Invoice.java, InvoiceCreateRequest.java, InvoiceResponse.java,
InvoiceUpdateDto.java, invoiceCreateForm.jsx, invoiceEditForm.js, invoiceDetail.js,
ordersTable.jsx

---

## Task 1 — Backend: Customer stats

### `dto/FinanceDashboardResponse.java`
Add one field to the record:
```java
List<Map<String,Object>> customerStats
```
The record currently ends with `BigDecimal netProfit`. Add `customerStats` after it.
Keep all existing fields — do not remove anything (backward compat).

### `service/FinanceService.java`
1. Add a new private method `getMonthlyCustomerStats(List<Invoice> invoices)`:
   - Group invoices by customer name: `invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Sin cliente"`
   - For each group compute:
     - `income` = sum of `invoice.getPrecio()`
     - `itemCount` = sum of `invoice.getLineItems().size()` (use 0 if lineItems null)
   - Return `List<Map<String, Object>>` where each map has keys: `"label"`, `"income"`, `"itemCount"`
   - Sort descending by income

2. In the `dashboard()` method, call `getMonthlyCustomerStats(invoices)` and pass the result
   as the new `customerStats` argument when constructing `FinanceDashboardResponse`.

---

## Task 2 — Frontend: Finance panel cleanup

### `src/views/finances.jsx`

**A. Remove furniture metrics from Admin KPI section (around line 215-230):**
- Delete the `StatCard` for `'CMV (Entregados)'` (`financeData.tCogs`)
- Delete the `StatCard` for `'Ganancia Bruta'` (`financeData.grossProfit`)
- Add a new `StatCard` for `'Pendiente de Cobro'`:
  ```jsx
  <StatCard
    title='Pendiente de Cobro'
    value={Number(financeData.tInc || 0) - Number(financeData.tDep || 0)}
    icon='⏳'
    borderColor='#e17055'
  />
  ```
- New layout: 5 cards in 2 rows
  - Row 1 (3 cards): Ingresos Totales · Efectivo Recibido · Pendiente de Cobro
  - Row 2 (2 cards): Gastos Operativos · Ganancia Neta

**B. Bar chart — swap to customer data:**
- Change `<ComparisonBarChart data={financeData.userStats} />` → `<ComparisonBarChart data={financeData.customerStats || []} />`
- Change title `"Rendimiento por Gestor"` → `"Rendimiento por Cliente"`

**C. Gestor view — rename stat card:**
- Change `title='Unidades Vendidas'` → `title='Facturas'`
- Change `icon='📦'` → `icon='🧾'`
- Value stays the same (`myStats.unitsSold` — it's already an invoice count)

**D. PDF export — replace `window.print()`:**

Add imports at top:
```jsx
import { pdf } from '@react-pdf/renderer';
import html2canvas from 'html2canvas';
import FinanceReportPdf from '../components/FinanceReportPdf';
```

Add refs (inside the component, after existing state):
```jsx
const barChartRef = useRef(null);
const pieChartRef = useRef(null);
```
Add `useRef` to the React import at the top.

Attach refs to the chart container divs:
```jsx
// Bar chart container div — add ref:
<div ref={barChartRef} className='card-white' style={{...}}>

// Pie chart container div — add ref:
<div ref={pieChartRef} className='card-white' style={{...}}>
```

Replace `handleExportPdf`:
```jsx
const handleExportPdf = async () => {
    const [barCanvas, pieCanvas] = await Promise.all([
        html2canvas(barChartRef.current, { scale: 2 }),
        html2canvas(pieChartRef.current, { scale: 2 }),
    ]);
    const barChartImg = barCanvas.toDataURL('image/png');
    const pieChartImg = pieCanvas.toDataURL('image/png');

    const blob = await pdf(
        <FinanceReportPdf
            financeData={financeData}
            barChartImg={barChartImg}
            pieChartImg={pieChartImg}
            aiInsight={aiInsight}
            selectedMonth={selectedMonth}
            userName={user?.username}
        />
    ).toBlob();

    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `reporte-financiero-${selectedMonth}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
};
```

**E. Remove `FinancePrintReport`:**
- Delete the entire `FinancePrintReport` function component at the bottom of the file
- Remove both `<FinancePrintReport ... />` usages in the JSX (one in Gestor return, one in Admin return)

---

## Task 3 — Frontend: Update bar chart component

### `src/components/comaprisonBarChart.jsx`

The component currently reads `d.unitsSold` and `d.userName`. Update to use customer data:

- `d.unitsSold` → `d.itemCount` (everywhere in the component)
- Bottom label text: `d.userName` → `d.label` (already exists as a field in the data)
- Purple bar label: currently shows the count number — keep as-is but value is now `d.itemCount`
- Legend/color semantics: purple bar = item count, green bar = income — no color change needed

---

## Task 4 — NEW FILE: `src/components/FinanceReportPdf.jsx`

Create using `@react-pdf/renderer`. Install first:
```bash
cd frontEnd/workflow && npm install @react-pdf/renderer html2canvas
```

```jsx
import React from 'react';
import { Document, Page, Text, View, Image, StyleSheet } from '@react-pdf/renderer';

const styles = StyleSheet.create({
    page: { padding: 40, fontFamily: 'Helvetica', backgroundColor: '#ffffff' },
    header: { flexDirection: 'row', justifyContent: 'space-between',
              alignItems: 'flex-start', marginBottom: 24,
              borderBottom: '2px solid #6c5ce7', paddingBottom: 12 },
    brand: { fontSize: 20, fontWeight: 'bold', color: '#6c5ce7' },
    reportTitle: { fontSize: 13, color: '#2d3436', marginTop: 4 },
    period: { fontSize: 10, color: '#636e72', marginTop: 2 },
    metaRight: { alignItems: 'flex-end' },
    metaLabel: { fontSize: 8, color: '#b2bec3' },
    metaValue: { fontSize: 10, color: '#2d3436', fontWeight: 'bold' },
    kpiGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 24 },
    kpiCard: { width: '18%', backgroundColor: '#f8f9fa', borderRadius: 6,
               padding: 10, borderLeft: '3px solid #6c5ce7' },
    kpiLabel: { fontSize: 8, color: '#636e72', marginBottom: 4 },
    kpiValue: { fontSize: 12, fontWeight: 'bold', color: '#2d3436' },
    sectionTitle: { fontSize: 11, fontWeight: 'bold', color: '#2d3436',
                    marginBottom: 8, marginTop: 16 },
    aiText: { fontSize: 10, color: '#636e72', lineHeight: 1.6,
              backgroundColor: '#f8f9fa', padding: 12, borderRadius: 6,
              borderLeft: '3px solid #6c5ce7' },
    chartImage: { width: '100%', marginTop: 8, borderRadius: 6 },
    footer: { position: 'absolute', bottom: 30, left: 40, right: 40,
              borderTop: '1px solid #dfe6e9', paddingTop: 8,
              flexDirection: 'row', justifyContent: 'space-between' },
    footerText: { fontSize: 8, color: '#b2bec3' },
});

const formatMoney = (value) =>
    '$' + Number(value || 0).toLocaleString('es-AR', { maximumFractionDigits: 0 });

const formatMonth = (value) => {
    if (!value) return '';
    const [year, month] = value.split('-');
    return new Date(Number(year), Number(month) - 1, 1)
        .toLocaleDateString('es-AR', { month: 'long', year: 'numeric' });
};

export default function FinanceReportPdf({
    financeData, barChartImg, pieChartImg, aiInsight, selectedMonth, userName
}) {
    const pendiente = Number(financeData.tInc || 0) - Number(financeData.tDep || 0);
    const kpis = [
        { label: 'Ingresos Totales',     value: formatMoney(financeData.tInc),    color: '#00b894' },
        { label: 'Efectivo Recibido',    value: formatMoney(financeData.tDep),    color: '#0984e3' },
        { label: 'Pendiente de Cobro',   value: formatMoney(pendiente),           color: '#e17055' },
        { label: 'Gastos Operativos',    value: formatMoney(financeData.tExp),    color: '#ff7675' },
        { label: 'Ganancia Neta',        value: formatMoney(financeData.netProfit), color: '#6c5ce7' },
    ];

    return (
        <Document>
            <Page size='A4' style={styles.page}>
                {/* Header */}
                <View style={styles.header}>
                    <View>
                        <Text style={styles.brand}>FinanzasFlow</Text>
                        <Text style={styles.reportTitle}>Reporte Financiero</Text>
                        <Text style={styles.period}>Período: {formatMonth(selectedMonth)}</Text>
                    </View>
                    <View style={styles.metaRight}>
                        <Text style={styles.metaLabel}>GENERADO</Text>
                        <Text style={styles.metaValue}>{new Date().toLocaleDateString('es-AR')}</Text>
                        <Text style={[styles.metaLabel, { marginTop: 6 }]}>USUARIO</Text>
                        <Text style={styles.metaValue}>{userName || '-'}</Text>
                    </View>
                </View>

                {/* KPI Cards */}
                <View style={styles.kpiGrid}>
                    {kpis.map(({ label, value, color }) => (
                        <View key={label} style={[styles.kpiCard, { borderLeftColor: color }]}>
                            <Text style={styles.kpiLabel}>{label}</Text>
                            <Text style={styles.kpiValue}>{value}</Text>
                        </View>
                    ))}
                </View>

                {/* AI Analysis */}
                <Text style={styles.sectionTitle}>Análisis IA</Text>
                <Text style={styles.aiText}>
                    {aiInsight || 'No se generó análisis IA para este período.'}
                </Text>

                {/* Charts */}
                {barChartImg && (
                    <>
                        <Text style={styles.sectionTitle}>Rendimiento por Cliente</Text>
                        <Image src={barChartImg} style={styles.chartImage} />
                    </>
                )}
                {pieChartImg && (
                    <>
                        <Text style={styles.sectionTitle}>Distribución de Gastos</Text>
                        <Image src={pieChartImg} style={styles.chartImage} />
                    </>
                )}

                {/* Footer */}
                <View style={styles.footer} fixed>
                    <Text style={styles.footerText}>FinanzasFlow — Reporte generado automáticamente</Text>
                    <Text style={styles.footerText}>{formatMonth(selectedMonth)}</Text>
                </View>
            </Page>
        </Document>
    );
}
```

---

## Verification
- [ ] `GET /api/finance` response includes `customerStats` array with `label`, `income`, `itemCount`
- [ ] Finance panel: 5 KPI cards visible (Ingresos, Efectivo Recibido, Pendiente de Cobro, Gastos, Ganancia Neta) — no CMV, no Ganancia Bruta
- [ ] Bar chart shows customer names (not gestor names)
- [ ] "Exportar PDF" downloads a `.pdf` file with all sections populated
- [ ] PDF includes AI insight text (or placeholder if not generated)
- [ ] Gestor view shows "Facturas" stat card (not "Unidades Vendidas")
