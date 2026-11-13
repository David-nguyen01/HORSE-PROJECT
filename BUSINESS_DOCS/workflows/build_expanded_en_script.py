import xml.etree.ElementTree as ET
from pathlib import Path

OUT_FILE = Path(__file__).with_name('CLUB_MANAGER_WORKFLOW_EN.drawio')
W, H = 1920, 1180

C = {
    'bg': '#FFFFFF',
    'ink': '#0F172A',
    'muted': '#475569',
    'border': '#94A3B8',
    'line': '#334155',
    'cm_fill': '#EFF6FF',
    'cm_stroke': '#2563EB',
    'cm_text': '#1E3A8A',
    'sys_fill': '#F1F5F9',
    'sys_stroke': '#475569',
    'sys_text': '#0F172A',
    'other_fill': '#F8FAFC',
    'other_stroke': '#64748B',
    'other_text': '#334155',
    'dec_fill': '#FEF3C7',
    'dec_stroke': '#D97706',
    'dec_text': '#92400E',
    'ok_fill': '#DCFCE7',
    'ok_stroke': '#16A34A',
    'ok_text': '#166534',
    'err_fill': '#FEE2E2',
    'err_stroke': '#DC2626',
    'err_text': '#991B1B',
    'start_fill': '#E0E7FF',
    'start_stroke': '#4F46E5',
    'start_text': '#3730A3',
    'sec_text': '#1E40AF',
}

pages = []

class DiagramPage:
    def __init__(self, num, tab_name, title, subtitle):
        self.num = num
        self.name = tab_name
        self.title = title
        self.subtitle = subtitle
        self.nodes = {}
        self.edges = []
        self.count = 1
        
        self.diagram = ET.Element('diagram', {'id': f'flow-{num:02d}', 'name': f'{num:02d} · {tab_name}'})
        model = ET.SubElement(self.diagram, 'mxGraphModel', {
            'dx': str(W), 'dy': str(H), 'grid': '1', 'gridSize': '10',
            'guides': '1', 'tooltips': '1', 'connect': '1', 'arrows': '1',
            'fold': '1', 'page': '1', 'pageScale': '1',
            'pageWidth': str(W), 'pageHeight': str(H),
            'background': '#FFFFFF', 'math': '0', 'shadow': '0'
        })
        self.root = ET.SubElement(model, 'root')
        ET.SubElement(self.root, 'mxCell', {'id': '0'})
        ET.SubElement(self.root, 'mxCell', {'id': '1', 'parent': '0'})
        
        self._add_header()
        pages.append(self)
        
    def _uid(self):
        self.count += 1
        return f'p{self.num}_{self.count}'
        
    def _add_header(self):
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': '',
            'style': f'shape=rect;rounded=0;fillColor={C["cm_stroke"]};strokeColor=none;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '50', 'y': '35', 'width': '8', 'height': '75', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': 'STRIDE  /  RACEHORSE TRAINING MANAGEMENT SYSTEM',
            'style': f'text;html=0;fontSize=13;fontStyle=1;fontColor={C["cm_stroke"]};align=left;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '75', 'y': '32', 'width': '800', 'height': '22', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': self.title,
            'style': f'text;html=0;fontSize=24;fontStyle=1;fontColor={C["ink"]};align=left;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '75', 'y': '58', 'width': '1250', 'height': '32', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': self.subtitle,
            'style': f'text;html=0;fontSize=14;fontStyle=0;fontColor={C["muted"]};align=left;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '75', 'y': '90', 'width': '1450', 'height': '24', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': f'CLUB MANAGER  /  FLOW {self.num:02d}',
            'style': f'text;html=0;fontSize=13;fontStyle=1;fontColor={C["cm_stroke"]};align=right;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '1550', 'y': '35', 'width': '310', 'height': '24', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'style': f'edgeStyle=none;html=0;endArrow=none;strokeColor={C["border"]};strokeWidth=1;',
            'parent': '1', 'edge': '1'
        })
        g = ET.SubElement(c, 'mxGeometry', {'relative': '1', 'as': 'geometry'})
        ET.SubElement(g, 'mxPoint', {'x': '50', 'y': '125', 'as': 'sourcePoint'})
        ET.SubElement(g, 'mxPoint', {'x': '1870', 'y': '125', 'as': 'targetPoint'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': 'RTMS Architecture · Local Source Aligned (Branch: develop · Commit 63b9362) · Detailed Academic Workflow',
            'style': f'text;html=0;fontSize=11;fontColor={C["muted"]};align=left;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '50', 'y': '1145', 'width': '1000', 'height': '20', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': f'PAGE {self.num:02d} / 10   •   EDITABLE IN DRAW.IO',
            'style': f'text;html=0;fontSize=11;fontColor={C["muted"]};align=right;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '1550', 'y': '1145', 'width': '320', 'height': '20', 'as': 'geometry'})

    def section(self, y, text):
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': text,
            'style': f'text;html=0;fontSize=14;fontStyle=1;fontColor={C["sec_text"]};align=left;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': '50', 'y': str(y), 'width': '1800', 'height': '25', 'as': 'geometry'})

    def start_node(self, nid, x, y, label='Start', w=130, h=45):
        c = ET.SubElement(self.root, 'mxCell', {
            'id': nid, 'value': label,
            'style': f'rounded=1;arcSize=50;whiteSpace=wrap;html=0;fontSize=12;fontStyle=1;fillColor={C["start_fill"]};strokeColor={C["start_stroke"]};fontColor={C["start_text"]};align=center;verticalAlign=middle;strokeWidth=1.8;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
        self.nodes[nid] = (x, y, w, h)
        return nid

    def end_node(self, nid, x, y, label='End', kind='ok', w=160, h=45):
        fill = C['ok_fill'] if kind == 'ok' else C['err_fill']
        stroke = C['ok_stroke'] if kind == 'ok' else C['err_stroke']
        text_col = C['ok_text'] if kind == 'ok' else C['err_text']
        c = ET.SubElement(self.root, 'mxCell', {
            'id': nid, 'value': label,
            'style': f'rounded=1;arcSize=50;whiteSpace=wrap;html=0;fontSize=12;fontStyle=1;fillColor={fill};strokeColor={stroke};fontColor={text_col};align=center;verticalAlign=middle;strokeWidth=1.8;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
        self.nodes[nid] = (x, y, w, h)
        return nid

    def action(self, nid, x, y, text, role='CLUB MANAGER', w=210, h=75, kind='cm'):
        if kind == 'cm':
            fill, stroke, text_col = C['cm_fill'], C['cm_stroke'], C['cm_text']
        elif kind == 'sys':
            fill, stroke, text_col = C['sys_fill'], C['sys_stroke'], C['sys_text']
        else:
            fill, stroke, text_col = C['other_fill'], C['other_stroke'], C['other_text']
            
        val = f'[{role}]\n{text}' if role else text
        c = ET.SubElement(self.root, 'mxCell', {
            'id': nid, 'value': val,
            'style': f'rounded=1;arcSize=10;whiteSpace=wrap;html=0;fontSize=12;fontStyle=0;fillColor={fill};strokeColor={stroke};fontColor={text_col};align=center;verticalAlign=middle;strokeWidth=1.5;spacing=4;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
        self.nodes[nid] = (x, y, w, h)
        return nid

    def decision(self, nid, x, y, text, w=180, h=75):
        c = ET.SubElement(self.root, 'mxCell', {
            'id': nid, 'value': text,
            'style': f'rhombus;whiteSpace=wrap;html=0;fontSize=11;fontStyle=1;fillColor={C["dec_fill"]};strokeColor={C["dec_stroke"]};fontColor={C["dec_text"]};align=center;verticalAlign=middle;strokeWidth=1.5;spacing=2;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
        self.nodes[nid] = (x, y, w, h)
        return nid

    def error_box(self, nid, x, y, text, w=200, h=65):
        c = ET.SubElement(self.root, 'mxCell', {
            'id': nid, 'value': text,
            'style': f'rounded=1;arcSize=10;whiteSpace=wrap;html=0;fontSize=11;fontStyle=1;fillColor={C["err_fill"]};strokeColor={C["err_stroke"]};fontColor={C["err_text"]};align=center;verticalAlign=middle;strokeWidth=1.5;spacing=3;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
        self.nodes[nid] = (x, y, w, h)
        return nid

    def edge(self, src, tgt, label='', sp='r', tp='l', via=None, color=None, dashed=False):
        port = {'r': (1, 0.5), 'l': (0, 0.5), 'b': (0.5, 1), 't': (0.5, 0)}
        a, b = port[sp], port[tp]
        col = color or C['line']
        dash_str = 'dashed=1;' if dashed else ''
        sty = f'edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;jettySize=auto;html=0;endArrow=classic;endFill=1;strokeColor={col};strokeWidth=1.8;{dash_str}exitX={a[0]};exitY={a[1]};entryX={b[0]};entryY={b[1]};fontSize=11;fontStyle=1;fontColor={col};labelBackgroundColor=#FFFFFF;'
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': label,
            'style': sty, 'parent': '1', 'source': src, 'target': tgt, 'edge': '1'
        })
        g = ET.SubElement(c, 'mxGeometry', {'relative': '1', 'as': 'geometry'})
        if via:
            arr = ET.SubElement(g, 'Array', {'as': 'points'})
            for vx, vy in via:
                ET.SubElement(arr, 'mxPoint', {'x': str(vx), 'y': str(vy)})
        self.edges.append((src, tgt, label))

    def note_box(self, title, body, x=50, y=990, w=1820, h=130, tone='blue'):
        border_col = C['cm_stroke'] if tone == 'blue' else C['dec_stroke'] if tone == 'amber' else C['ok_stroke']
        fill_col = C['cm_fill'] if tone == 'blue' else C['dec_fill'] if tone == 'amber' else C['ok_fill']
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': '',
            'style': f'rounded=1;arcSize=8;fillColor={fill_col};strokeColor={border_col};strokeWidth=1.5;html=0;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': f'BUSINESS RULES & TECHNICAL CONSTRAINTS: {title}',
            'style': f'text;html=0;fontSize=13;fontStyle=1;fontColor={border_col};align=left;verticalAlign=middle;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x + 20), 'y': str(y + 10), 'width': str(w - 40), 'height': '22', 'as': 'geometry'})
        
        c = ET.SubElement(self.root, 'mxCell', {
            'id': self._uid(), 'value': body,
            'style': f'text;html=0;fontSize=12;fontStyle=0;fontColor={C["ink"]};align=left;verticalAlign=top;spacing=2;',
            'parent': '1', 'vertex': '1'
        })
        ET.SubElement(c, 'mxGeometry', {'x': str(x + 20), 'y': str(y + 35), 'width': str(w - 40), 'height': str(h - 45), 'as': 'geometry'})

print("Diagram framework class configured")

# =========================================================================
# TAB 00: OVERVIEW (Master Operational Architecture)
# =========================================================================
def build_tab_00():
    p = DiagramPage(0, "Overview", 
                    "END-TO-END OPERATIONAL ARCHITECTURE · CLUB MANAGER",
                    "Complete master lifecycle connecting Authentication, RBAC, Facilities, Horse Intake, Supply Chain, Racing, and Audit Oversight.")
    
    p.section(140, "STAGE 1: SYSTEM AUTHENTICATION & CORE ROLE MANAGEMENT")
    p.start_node('p0_start', 50, 185, 'System Start')
    p.action('p0_login', 220, 175, 'Enter Credentials\n(Username & Password)', 'CLUB MANAGER')
    p.decision('p0_auth_check', 470, 175, 'Valid Credentials\n& ROLE_CLUB_MANAGER?', 190, 75)
    p.error_box('p0_auth_err', 470, 280, 'Access Denied:\nCheck Credentials or Role')
    p.action('p0_dash', 700, 175, 'Access Administration\nDashboard (/)', 'CLUB MANAGER', kind='ok')
    
    p.action('p0_m01', 950, 175, 'Flow 01: Manage Users,\nPasswords & Status', 'CLUB MANAGER')
    p.action('p0_m02', 1200, 175, 'Flow 02: Dynamic RBAC\nPermissions Matrix', 'CLUB MANAGER')
    p.action('p0_m05', 1450, 175, 'Flow 05: Setup Stables\n& Stalls (1-4 Stalls/Unit)', 'CLUB MANAGER')
    p.action('p0_m03', 1700, 175, 'Flow 03: Registry & Service\nGuarantees (Override)', 'CLUB MANAGER')
    
    p.edge('p0_start', 'p0_login')
    p.edge('p0_login', 'p0_auth_check')
    p.edge('p0_auth_check', 'p0_auth_err', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p0_auth_check', 'p0_dash', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p0_dash', 'p0_m01')
    p.edge('p0_m01', 'p0_m02')
    p.edge('p0_m02', 'p0_m05')
    p.edge('p0_m05', 'p0_m03')
    
    p.section(390, "STAGE 2: HORSE INTAKE, CARE COORDINATION & FACILITY ALLOCATION")
    p.action('p0_m04_recept', 50, 430, 'Flow 04A: Verify Payment\n& Reception in Waiting Room', 'STABLE STAFF', kind='other')
    p.action('p0_m04_vet', 300, 430, 'Flow 04B: Clinical Exam,\n3D Injuries & FIT Verdict', 'VETERINARIAN', kind='other')
    p.action('p0_m05_assign', 550, 430, 'Flow 05B: Assign Vacant\nStall (Max 4 Horses Rule)', 'CLUB MANAGER')
    p.action('p0_m04_trainer', 800, 430, 'Flow 04C: Head Trainer\nIntake & Schedule Training', 'HEAD TRAINER', kind='other')
    
    p.edge('p0_m03', 'p0_m04_recept', sp='b', tp='t', via=[(1805, 345), (155, 345)])
    p.edge('p0_m04_recept', 'p0_m04_vet')
    p.edge('p0_m04_vet', 'p0_m05_assign')
    p.edge('p0_m05_assign', 'p0_m04_trainer')
    
    p.section(550, "STAGE 3: DAILY OPERATIONS, SUPPLY CHAIN & RACING COMPETITIONS")
    p.action('p0_m06_replenish', 50, 590, 'Flow 06: Review & Approve\nReplenishment Proposals', 'CLUB MANAGER')
    p.action('p0_m06_receipt', 300, 590, 'Flow 06B: Goods Receipt\n& Physical Stock Increment', 'STABLE STAFF', kind='other')
    p.action('p0_m07_usage', 550, 590, 'Flow 07: Consume Stock &\nAuto-book Expense in Ledger', 'SYSTEM', kind='sys')
    p.action('p0_m08_race', 800, 590, 'Flow 08: Organize Races &\nConfirm Verified Entries', 'CLUB MANAGER')
    
    p.edge('p0_m04_trainer', 'p0_m06_replenish', sp='b', tp='t', via=[(905, 520), (155, 520)])
    p.edge('p0_m06_replenish', 'p0_m06_receipt')
    p.edge('p0_m06_receipt', 'p0_m07_usage')
    p.edge('p0_m07_usage', 'p0_m08_race')
    
    p.section(710, "STAGE 4: FINANCIAL ACCOUNTABILITY, AUDIT VERIFICATION & GOVERNANCE")
    p.action('p0_m09_ledger', 50, 750, 'Flow 09A: General Ledger\nIncome / Expense Booking', 'CLUB MANAGER')
    p.action('p0_m09_report', 300, 750, 'Flow 09B: MoM Variance &\nExport Financial Statements', 'CLUB MANAGER')
    p.action('p0_m10_audit', 550, 750, 'Flow 10: Inspect Immutable\nSystem Audit Trail & Diffs', 'CLUB MANAGER')
    p.end_node('p0_end_gov', 800, 765, 'Governance Complete', kind='ok')
    
    p.edge('p0_m08_race', 'p0_m09_ledger', sp='b', tp='t', via=[(905, 680), (155, 680)])
    p.edge('p0_m09_ledger', 'p0_m09_report')
    p.edge('p0_m09_report', 'p0_m10_audit')
    p.edge('p0_m10_audit', 'p0_end_gov')
    
    p.note_box("SYSTEM ROLES & ARCHITECTURAL STRUCTURE",
               "1. Master Role Boundaries: CLUB_MANAGER is the executive administrator overseeing facilities, finances, personnel and race confirmation.\n"
               "2. Operational Modularity: Every workflow (01 through 10) maps to dedicated Spring Boot REST Controllers, JPA Entities, and Database Trigger Guards.\n"
               "3. End-to-End Traceability: State-changing operations across all phases trigger automatic append-only logging in dbo.audit_logs.",
               tone='blue')

# =========================================================================
# TAB 01: ACCOUNTS (User Account Lifecycle & Security Guard)
# =========================================================================
def build_tab_01():
    p = DiagramPage(1, "Accounts",
                    "FLOW 01 · USER ACCOUNT LIFECYCLE & SECURITY GUARD",
                    "Atomic workflows for Account Creation, Profile Modification, Password Reset, and Deactivation Guard.")
    
    p.section(140, "SUB-FLOW A: CREATE NEW USER ACCOUNT (SEPARATE STEPS & VALIDATION)")
    p.start_node('p1_start_create', 50, 185, 'Start Create')
    p.action('p1_open_form', 220, 175, 'Click "Create User" in\nAccount Management', 'CLUB MANAGER')
    p.action('p1_enter_data', 470, 175, 'Enter Username, Email,\nTemp Password & Select Role', 'CLUB MANAGER')
    p.decision('p1_check_form', 720, 175, 'Form Format Valid?\n(Email & Required Fields)', 180, 75)
    p.error_box('p1_err_form', 720, 275, 'Invalid Input:\nShow Field Error Hints')
    p.decision('p1_check_unique', 950, 175, 'Username & Email\nUnique in Database?', 180, 75)
    p.error_box('p1_err_unique', 950, 275, 'Duplicate Account:\nUsername/Email Exists')
    p.action('p1_bcrypt', 1180, 175, 'Hash Password using\nBCryptPasswordEncoder', 'SYSTEM', kind='sys')
    p.action('p1_save_user', 1420, 175, 'Insert into dbo.users\nand dbo.user_roles', 'SYSTEM', kind='sys')
    p.action('p1_audit_create', 1660, 175, 'Log Account Creation\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p1_end_create', 1685, 275, 'Account Created', kind='ok')
    
    p.edge('p1_start_create', 'p1_open_form')
    p.edge('p1_open_form', 'p1_enter_data')
    p.edge('p1_enter_data', 'p1_check_form')
    p.edge('p1_check_form', 'p1_err_form', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p1_err_form', 'p1_enter_data', sp='l', tp='b', via=[(600, 310), (575, 310)], color=C['err_stroke'], dashed=True)
    p.edge('p1_check_form', 'p1_check_unique', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p1_check_unique', 'p1_err_unique', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p1_err_unique', 'p1_enter_data', sp='l', tp='b', via=[(830, 310), (575, 310)], color=C['err_stroke'], dashed=True)
    p.edge('p1_check_unique', 'p1_bcrypt', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p1_bcrypt', 'p1_save_user')
    p.edge('p1_save_user', 'p1_audit_create')
    p.edge('p1_audit_create', 'p1_end_create', sp='b', tp='t')
    
    p.section(380, "SUB-FLOW B: EDIT USER PROFILE DETAILS (NAME & CONTACT)")
    p.start_node('p1_start_edit', 50, 425, 'Start Edit')
    p.action('p1_select_edit', 220, 415, 'Select Target Account\nfrom User List', 'CLUB MANAGER')
    p.action('p1_input_edit', 470, 415, 'Update Display Name,\nPhone & Address Details', 'CLUB MANAGER')
    p.decision('p1_val_edit', 720, 415, 'Input Valid & Fits\nField Constraints?', 180, 75)
    p.error_box('p1_err_edit', 720, 515, 'Validation Failed:\nCorrect Input Fields')
    p.action('p1_save_edit', 950, 415, 'Execute UPDATE on\ndbo.users Table', 'SYSTEM', kind='sys')
    p.action('p1_audit_edit', 1180, 415, 'Log Profile Update\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p1_end_edit', 1420, 430, 'Profile Updated', kind='ok')
    
    p.edge('p1_start_edit', 'p1_select_edit')
    p.edge('p1_select_edit', 'p1_input_edit')
    p.edge('p1_input_edit', 'p1_val_edit')
    p.edge('p1_val_edit', 'p1_err_edit', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p1_val_edit', 'p1_save_edit', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p1_save_edit', 'p1_audit_edit')
    p.edge('p1_audit_edit', 'p1_end_edit')
    
    p.section(610, "SUB-FLOW C: DEACTIVATE / LOCK ACCOUNT (ACCOUNT ACTIVITY GUARD ENFORCEMENT)")
    p.start_node('p1_start_lock', 50, 655, 'Start Deactivate')
    p.action('p1_select_lock', 220, 645, 'Select Target Account &\nClick "Deactivate"', 'CLUB MANAGER')
    p.decision('p1_check_self', 470, 645, 'Is Target Account\nCurrent Manager?', 180, 75)
    p.error_box('p1_err_self', 470, 750, 'Blocked: Cannot\nDeactivate Own Account')
    p.decision('p1_check_last_adm', 720, 645, 'Is Target the Final\nActive Club Manager?', 180, 75)
    p.error_box('p1_err_last_adm', 720, 750, 'Blocked: Must Keep >= 1\nActive Club Manager')
    p.decision('p1_check_guard', 950, 645, 'AccountActivityGuard:\nAny Active Duties Assigned?', 190, 75)
    p.error_box('p1_err_guard', 950, 750, 'Blocked: Handover Required\n(Trainer/Vet/Staff/Owner)')
    p.action('p1_set_disabled', 1190, 645, 'Set users.enabled = false\nin Database', 'SYSTEM', kind='sys')
    p.action('p1_revoke_tokens', 1420, 645, 'Revoke All Active JWT\nTokens & Clear Cache', 'SYSTEM', kind='sys')
    p.action('p1_audit_lock', 1650, 645, 'Log Security Deactivation\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p1_end_lock', 1675, 750, 'User Deactivated', kind='err')
    
    p.edge('p1_start_lock', 'p1_select_lock')
    p.edge('p1_select_lock', 'p1_check_self')
    p.edge('p1_check_self', 'p1_err_self', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p1_check_self', 'p1_check_last_adm', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p1_check_last_adm', 'p1_err_last_adm', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p1_check_last_adm', 'p1_check_guard', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p1_check_guard', 'p1_err_guard', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p1_check_guard', 'p1_set_disabled', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p1_set_disabled', 'p1_revoke_tokens')
    p.edge('p1_revoke_tokens', 'p1_audit_lock')
    p.edge('p1_audit_lock', 'p1_end_lock', sp='b', tp='t')
    
    p.note_box("SECURITY CONSTRAINTS & HANDOVER ENFORCEMENT",
               "1. AccountActivityGuard: System prevents deactivating or re-roling accounts actively linked to live horses (current_trainer_id, diagnosing_vet_user_id, caretaker_user_id, horse_ownerships).\n"
               "2. Token Eviction: Disabling an account immediately invalidates outstanding JWT refresh tokens, preventing API calls upon next token refresh.\n"
               "3. Re-enabling: Setting enabled = true allows login again but does NOT restore previous expired tokens.",
               tone='amber')

# =========================================================================
# TAB 02: ROLES AND PERMISSIONS (Role Reassignment & Dynamic RBAC)
# =========================================================================
def build_tab_02():
    p = DiagramPage(2, "Roles and permissions",
                    "FLOW 02 · ROLE REASSIGNMENT & DYNAMIC RBAC MATRIX",
                    "Separate workflows for individual account role changes and global dynamic permission matrix configuration.")
    
    p.section(140, "SUB-FLOW A: REASSIGN ACCOUNT ROLE (AUDIT TRAIL & RESPONSIBILITY PRESERVATION)")
    p.start_node('p2_start_role', 50, 185, 'Start Role Change')
    p.action('p2_select_user', 220, 175, 'Select Target Account &\nClick "Change Role"', 'CLUB MANAGER')
    p.action('p2_pick_role', 470, 175, 'Select New Role from 5 Types\n(e.g. STABLE_STAFF)', 'CLUB MANAGER')
    p.decision('p2_check_same', 720, 175, 'Selected Role Same\nas Current Role?', 180, 75)
    p.error_box('p2_err_same', 720, 275, 'No Change: Existing\nRole Maintained')
    p.decision('p2_check_self_role', 950, 175, 'Target Account is\nCurrent Manager?', 180, 75)
    p.error_box('p2_err_self_role', 950, 275, 'Blocked: Cannot Modify\nOwn Manager Role')
    p.decision('p2_check_history', 1180, 175, 'Account Has Historical\nor Active Business Data?', 190, 75)
    p.error_box('p2_err_history', 1180, 275, 'Blocked: Historical Audit Lock\n(Create New Account Instead)')
    p.action('p2_update_role_db', 1420, 175, 'Update dbo.user_roles\nwith New Role Mapping', 'SYSTEM', kind='sys')
    p.action('p2_revoke_role_jwt', 1660, 175, 'Revoke Sessions to Force\nImmediate Re-login', 'SYSTEM', kind='sys')
    p.end_node('p2_end_role', 1685, 275, 'Role Reassigned', kind='ok')
    
    p.edge('p2_start_role', 'p2_select_user')
    p.edge('p2_select_user', 'p2_pick_role')
    p.edge('p2_pick_role', 'p2_check_same')
    p.edge('p2_check_same', 'p2_err_same', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p2_check_same', 'p2_check_self_role', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p2_check_self_role', 'p2_err_self_role', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p2_check_self_role', 'p2_check_history', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p2_check_history', 'p2_err_history', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p2_check_history', 'p2_update_role_db', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p2_update_role_db', 'p2_revoke_role_jwt')
    p.edge('p2_revoke_role_jwt', 'p2_end_role', sp='b', tp='t')
    
    p.section(450, "SUB-FLOW B: CONFIGURE DYNAMIC RBAC MATRIX (8 MODULES × 5 ROLES)")
    p.start_node('p2_start_matrix', 50, 495, 'Start RBAC Matrix')
    p.action('p2_open_matrix', 220, 485, 'Navigate to Permissions\nMatrix (/admin/permissions)', 'CLUB MANAGER')
    p.action('p2_select_role_tab', 470, 485, 'Select Target Role Tab\n(e.g. VETERINARIAN)', 'CLUB MANAGER')
    p.decision('p2_check_cm_matrix', 720, 485, 'Target Role is\nCLUB_MANAGER?', 180, 75)
    p.error_box('p2_err_cm_matrix', 720, 585, 'Protected: CLUB_MANAGER\nRetains Full Master Permissions')
    p.action('p2_toggle_boxes', 950, 485, 'Toggle VIEW, EDIT, APPROVE,\nCLAIM Checkboxes per Module', 'CLUB MANAGER')
    p.decision('p2_check_perm_valid', 1190, 485, 'Module-Authority Pair\nSupported by Backend?', 190, 75)
    p.error_box('p2_err_perm_invalid', 1190, 585, 'Rejected: Unsupported\nAuthority Configuration')
    p.action('p2_save_matrix', 1430, 485, 'Upsert Records in\ndbo.role_permissions', 'SYSTEM', kind='sys')
    p.action('p2_audit_matrix', 1660, 485, 'Log Permission Matrix\nChange in dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p2_end_matrix', 1685, 585, 'Permissions Active', kind='ok')
    
    p.edge('p2_start_matrix', 'p2_open_matrix')
    p.edge('p2_open_matrix', 'p2_select_role_tab')
    p.edge('p2_select_role_tab', 'p2_check_cm_matrix')
    p.edge('p2_check_cm_matrix', 'p2_err_cm_matrix', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p2_check_cm_matrix', 'p2_toggle_boxes', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p2_toggle_boxes', 'p2_check_perm_valid')
    p.edge('p2_check_perm_valid', 'p2_err_perm_invalid', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p2_check_perm_valid', 'p2_save_matrix', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p2_save_matrix', 'p2_audit_matrix')
    p.edge('p2_audit_matrix', 'p2_end_matrix', sp='b', tp='t')
    
    p.note_box("RBAC ARCHITECTURE & IMMUTABLE ROLES",
               "1. Five Fixed System Roles: CLUB_MANAGER, HEAD_TRAINER, STABLE_STAFF, HORSE_OWNER, VETERINARIAN.\n"
               "2. Immutability of CLUB_MANAGER: Club Manager authorities cannot be toggled off to prevent system lockout.\n"
               "3. Historical Accountability Lock: Once a user account has participated in veterinary cases, horse ownership or training logs, its role cannot be changed to prevent data corruption.",
               tone='blue')

# =========================================================================
# TAB 03: HORSE PROFILES & GUARANTEES
# =========================================================================
def build_tab_03():
    p = DiagramPage(3, "Profiles and guarantees",
                    "FLOW 03 · HORSE REGISTRY & EXECUTIVE SERVICE GUARANTEES",
                    "Independent workflows for horse profile creation, microchip validation, 100% ownership, and financial service guarantee override.")
    
    p.section(140, "SUB-FLOW A: CREATE & MAINTAIN HORSE REGISTRY (STRICT IDENTITY & PEDIGREE)")
    p.start_node('p3_start_horse', 50, 185, 'Start Profile')
    p.action('p3_open_form', 220, 175, 'Open Horse Profile Form\nin /horses Portal', 'CLUB MANAGER')
    p.action('p3_input_data', 470, 175, 'Input Name, Code, Gender,\nDOB, Breed, Microchip No.', 'CLUB MANAGER')
    p.decision('p3_check_unique', 720, 175, 'Code & Microchip\nUnique in Database?', 180, 75)
    p.error_box('p3_err_unique', 720, 275, 'Duplicate Identifier:\nCode or Microchip Exists')
    p.action('p3_pedigree', 950, 175, 'Select Sire (Father) &\nDam (Mother) Bloodlines', 'CLUB MANAGER')
    p.action('p3_owner', 1180, 175, 'Assign Single Registered\nOwner (100% Share)', 'CLUB MANAGER')
    p.action('p3_docs', 1410, 175, 'Upload Passport, Health\nCertificate & ID Photos', 'CLUB MANAGER')
    p.action('p3_save_db', 1640, 175, 'Insert into dbo.horses &\ndbo.horse_ownerships', 'SYSTEM', kind='sys')
    p.end_node('p3_end_horse', 1665, 275, 'Profile Created\n(PENDING_WAITING_ROOM)', kind='ok')
    
    p.edge('p3_start_horse', 'p3_open_form')
    p.edge('p3_open_form', 'p3_input_data')
    p.edge('p3_input_data', 'p3_check_unique')
    p.edge('p3_check_unique', 'p3_err_unique', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p3_check_unique', 'p3_pedigree', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p3_pedigree', 'p3_owner')
    p.edge('p3_owner', 'p3_docs')
    p.edge('p3_docs', 'p3_save_db')
    p.edge('p3_save_db', 'p3_end_horse', sp='b', tp='t')
    
    p.section(400, "SUB-FLOW B: EXECUTIVE SERVICE GUARANTEE OVERRIDE (BYPASS PAYMENT GATE)")
    p.start_node('p3_start_ovr', 50, 445, 'Start Override')
    p.action('p3_filter_unpaid', 220, 435, 'View Horses with Unpaid or\nExpired Service Packages', 'CLUB MANAGER')
    p.action('p3_eval_case', 470, 435, 'Evaluate Special Case:\nVIP, Offline Wire, Emergency', 'CLUB MANAGER')
    p.decision('p3_dec_ovr', 720, 435, 'Does Club Manager Grant\nService Guarantee?', 180, 75)
    p.error_box('p3_wait_pay', 720, 535, 'Guarantee Denied:\nAwaiting Owner Payment')
    p.action('p3_input_reason', 950, 435, 'Enter Formal Justification\nin override_reason Field', 'CLUB MANAGER')
    p.action('p3_apply_ovr_db', 1190, 435, 'Set service_override = 1\nin dbo.horses', 'SYSTEM', kind='sys')
    p.action('p3_advance_status', 1430, 435, 'Advance intake_status to\n"WAITING_FOR_VET"', 'SYSTEM', kind='sys')
    p.action('p3_audit_ovr', 1660, 435, 'Log Financial Guarantee\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p3_end_ovr', 1685, 535, 'Cleared for Medical Intake\n(Continues to Flow 04)', kind='ok')
    
    p.edge('p3_start_ovr', 'p3_filter_unpaid')
    p.edge('p3_filter_unpaid', 'p3_eval_case')
    p.edge('p3_eval_case', 'p3_dec_ovr')
    p.edge('p3_dec_ovr', 'p3_wait_pay', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p3_dec_ovr', 'p3_input_reason', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p3_input_reason', 'p3_apply_ovr_db')
    p.edge('p3_apply_ovr_db', 'p3_advance_status')
    p.edge('p3_advance_status', 'p3_audit_ovr')
    p.edge('p3_audit_ovr', 'p3_end_ovr', sp='b', tp='t')
    
    p.note_box("OWNERSHIP & SERVICE OVERRIDE RULES",
               "1. Single Owner Invariant: Database enforces ux_horse_ownerships_one_owner ensuring exactly 1 active owner with 100% share per horse.\n"
               "2. Service Override Semantics: Setting service_override = 1 allows a horse to enter medical examination and stabling without wallet deduction. It does NOT create fictitious revenue.\n"
               "3. Revocation: If override is cancelled, the horse is immediately flagged as unpaid upon next billing cycle.",
               tone='blue')

print("Tabs 00-03 configured")

# =========================================================================
# TAB 04: HORSE INTAKE COORDINATION (Multi-Role Onboarding Pipeline)
# =========================================================================
def build_tab_04():
    p = DiagramPage(4, "Horse intake",
                    "FLOW 04 · MULTI-ROLE HORSE INTAKE & TRAINING CLEARANCE PIPELINE",
                    "Rigorous multi-stage handshake across Owner, Stable Staff, Veterinarian, Manager, and Head Trainer.")
    
    p.section(140, "STAGE 1: ADMISSION ELIGIBILITY & CLINICAL VETERINARY EXAMINATION")
    p.start_node('p4_start', 50, 185, 'Start Intake')
    p.decision('p4_check_elig', 220, 175, 'Is Service Package Paid\nOR Override = 1?', 180, 75)
    p.error_box('p4_err_unpaid', 220, 275, 'Blocked from Intake:\nAwaiting Fee or Override')
    p.action('p4_staff_recept', 460, 175, 'Verify Physical Arrival &\nTransfer to Waiting Room', 'STABLE STAFF', kind='other')
    p.action('p4_set_waiting', 700, 175, 'Update intake_status =\n"WAITING_FOR_VET"', 'SYSTEM', kind='sys')
    p.action('p4_vet_claim', 940, 175, 'Open Medical Queue &\nClaim Horse for Exam', 'VETERINARIAN', kind='other')
    p.action('p4_set_in_med', 1180, 175, 'Set intake_status="IN_MEDICAL"\n& Assign diagnosing_vet', 'SYSTEM', kind='sys')
    p.action('p4_vet_exam', 1420, 175, 'Perform Clinical Exam,\nCheck Vitals & Biometrics', 'VETERINARIAN', kind='other')
    p.decision('p4_check_injury', 1660, 175, 'Pathology or\nInjury Found?', 170, 75)
    
    p.edge('p4_start', 'p4_check_elig')
    p.edge('p4_check_elig', 'p4_err_unpaid', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p4_check_elig', 'p4_staff_recept', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p4_staff_recept', 'p4_set_waiting')
    p.edge('p4_set_waiting', 'p4_vet_claim')
    p.edge('p4_vet_claim', 'p4_set_in_med')
    p.edge('p4_set_in_med', 'p4_vet_exam')
    p.edge('p4_vet_exam', 'p4_check_injury')
    
    p.section(380, "STAGE 2: DIAGNOSIS VERDICT, 3D INJURY LOCKS & STALL ALLOCATION")
    p.action('p4_vet_lock', 1640, 420, 'Pin Injury on 3D Model &\nIssue Medical Training Lock', 'VETERINARIAN', kind='other')
    p.action('p4_mark_fit', 1420, 420, 'Set health_status = "FIT"\n(Cleared for Activity)', 'VETERINARIAN', kind='other')
    p.action('p4_complete_exam', 1180, 420, 'Complete Medical Exam:\nPOST /queue/{id}/complete', 'VETERINARIAN', kind='other')
    p.action('p4_set_pending_stall', 940, 420, 'Update intake_status =\n"PENDING_STALL"', 'SYSTEM', kind='sys')
    p.action('p4_mgr_assign_stall', 700, 420, 'Assign Vacant Stall in Unit\nvia Flow 05 (/admin/stables)', 'CLUB MANAGER')
    p.action('p4_set_stabled', 460, 420, 'Set intake_status="STABLED"\n& Link Active Stall Assignment', 'SYSTEM', kind='sys')
    
    p.edge('p4_check_injury', 'p4_vet_lock', 'Yes', sp='b', tp='t', color=C['dec_stroke'])
    p.edge('p4_check_injury', 'p4_mark_fit', 'No', sp='b', tp='t', color=C['ok_stroke'], via=[(1745, 360), (1525, 360)])
    p.edge('p4_vet_lock', 'p4_complete_exam', sp='l', tp='r')
    p.edge('p4_mark_fit', 'p4_complete_exam')
    p.edge('p4_complete_exam', 'p4_set_pending_stall')
    p.edge('p4_set_pending_stall', 'p4_mgr_assign_stall')
    p.edge('p4_mgr_assign_stall', 'p4_set_stabled')
    
    p.section(560, "STAGE 3: HEAD TRAINER INTAKE EVALUATION & TRAINING SCHEDULE COMMENCEMENT")
    p.decision('p4_check_trainer_elig', 460, 600, 'All Criteria Met?\nSTABLED + FIT + Vet Set\n+ No Active Medical Lock?', 210, 85)
    p.error_box('p4_hold_recovery', 220, 610, 'Hold in Stable:\nHorse Requires Medical Care;\nTraining Lock Active')
    p.action('p4_trainer_accept', 730, 605, 'Click "Accept for Training":\nPOST /trainer/{horseId}', 'HEAD TRAINER', kind='other')
    p.action('p4_link_trainer_db', 980, 605, 'Set current_trainer_id =\nTrainer in dbo.horses', 'SYSTEM', kind='sys')
    p.action('p4_create_plan', 1220, 605, 'Formulate Multi-phase\nTraining Plan & Schedules', 'HEAD TRAINER', kind='other')
    p.end_node('p4_end_intake', 1470, 620, 'Active in Training', kind='ok')
    
    p.edge('p4_set_stabled', 'p4_check_trainer_elig', sp='b', tp='t')
    p.edge('p4_check_trainer_elig', 'p4_hold_recovery', 'No', sp='l', tp='r', color=C['dec_stroke'])
    p.edge('p4_check_trainer_elig', 'p4_trainer_accept', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p4_trainer_accept', 'p4_link_trainer_db')
    p.edge('p4_link_trainer_db', 'p4_create_plan')
    p.edge('p4_create_plan', 'p4_end_intake')
    
    p.note_box("INTAKE GATES & HARD CLINICAL ENFORCEMENT",
               "1. Clinical Examination Invariant: Monthly health checkups and regular schedules cannot be created without a genuine clinical exam record.\n"
               "2. Injured Horses: Horses with active injuries or locks are still stabled for shelter, but strictly prohibited from appearing in trainer intake.\n"
               "3. Trainer Acceptance Criteria: intake_status == 'STABLED', health_status == 'FIT', diagnosing_vet_user_id IS NOT NULL, and no active locks in dbo.training_locks.",
               tone='blue')

# =========================================================================
# TAB 05: STABLES & STALLS (Facility Setup & Allocation)
# =========================================================================
def build_tab_05():
    p = DiagramPage(5, "Stables and stalls",
                    "FLOW 05 · STABLE FACILITY MANAGEMENT & STALL ALLOCATION",
                    "Rigid capacity enforcement (1-4 stalls per stable unit) and atomic assignment lifecycle tracking.")
    
    p.section(140, "SUB-FLOW A: FACILITY ARCHITECTURE (STABLE UNITS & STALL CODE REGISTRY)")
    p.start_node('p5_start_setup', 50, 185, 'Start Facility Setup')
    p.action('p5_open_admin', 220, 175, 'Navigate to Stables\nPortal (/admin/stables)', 'CLUB MANAGER')
    p.action('p5_input_unit', 470, 175, 'Enter Stable Unit Name,\nSector & Stall Code List', 'CLUB MANAGER')
    p.decision('p5_check_cap', 720, 175, 'Stalls Count per Stable\nbetween 1 and 4?', 180, 75)
    p.error_box('p5_err_cap', 720, 275, 'Capacity Violation:\nMust be 1 to 4 Stalls')
    p.decision('p5_check_unique', 950, 175, 'Stable Name & Stall\nCodes Unique?', 180, 75)
    p.error_box('p5_err_unique', 950, 275, 'Duplicate Identifier:\nStable or Stall Exists')
    p.action('p5_save_facility', 1190, 175, 'Insert into dbo.stables\nand dbo.stalls', 'SYSTEM', kind='sys')
    p.action('p5_audit_facility', 1430, 175, 'Log Facility Creation\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p5_end_setup', 1670, 190, 'Facility Ready', kind='ok')
    
    p.edge('p5_start_setup', 'p5_open_admin')
    p.edge('p5_open_admin', 'p5_input_unit')
    p.edge('p5_input_unit', 'p5_check_cap')
    p.edge('p5_check_cap', 'p5_err_cap', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p5_check_cap', 'p5_check_unique', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p5_check_unique', 'p5_err_unique', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p5_check_unique', 'p5_save_facility', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p5_save_facility', 'p5_audit_facility')
    p.edge('p5_audit_facility', 'p5_end_setup')
    
    p.section(420, "SUB-FLOW B: STALL ALLOCATION, TRANSFER & DISCHARGE (TRIGGER ENFORCED)")
    p.start_node('p5_start_assign', 50, 465, 'Start Assignment')
    p.action('p5_select_pair', 220, 455, 'Select Target Horse\nand Vacant Stall Code', 'CLUB MANAGER')
    p.decision('p5_check_trigger', 470, 455, 'DB Trigger Check:\nStall Vacant & Stable\nActive Horses <= 4?', 200, 80)
    p.error_box('p5_err_trigger', 470, 565, 'Trigger Blocked:\nStall Occupied or Stable\nExceeds 4 Horses Limit')
    p.decision('p5_check_transfer', 720, 455, 'Is this a Transfer from\nExisting Active Stall?', 180, 75)
    p.action('p5_terminate_old', 705, 565, 'Set previous assignment\nstatus = "ENDED"', 'SYSTEM', kind='sys')
    p.action('p5_create_new_assign', 960, 455, 'Insert STALL_ASSIGNMENT\nRecord (status="ACTIVE")', 'SYSTEM', kind='sys')
    p.action('p5_set_stabled_db', 1210, 455, 'Set horses.intake_status =\n"STABLED"', 'SYSTEM', kind='sys')
    p.action('p5_unassign_option', 1450, 455, 'Alternative: Release Stall\n(unassign) -> PENDING_STALL', 'CLUB MANAGER')
    p.end_node('p5_end_assign', 1690, 470, 'Stall Active', kind='ok')
    
    p.edge('p5_start_assign', 'p5_select_pair')
    p.edge('p5_select_pair', 'p5_check_trigger')
    p.edge('p5_check_trigger', 'p5_err_trigger', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p5_check_trigger', 'p5_check_transfer', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p5_check_transfer', 'p5_terminate_old', 'Yes', sp='b', tp='t')
    p.edge('p5_terminate_old', 'p5_create_new_assign', sp='r', tp='b', via=[(850, 602), (1065, 602)])
    p.edge('p5_check_transfer', 'p5_create_new_assign', 'No', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p5_create_new_assign', 'p5_set_stabled_db')
    p.edge('p5_set_stabled_db', 'p5_unassign_option')
    p.edge('p5_unassign_option', 'p5_end_assign')
    
    p.note_box("DATABASE TRIGGER GUARD: TR_STALL_ASSIGNMENT_UNIQUE_ACTIVE",
               "1. Stall Exclusivity: Prevents two active assignments for the same stall_id simultaneously.\n"
               "2. Horse Exclusivity: Prevents a single horse from holding multiple active stall assignments.\n"
               "3. Stable Capacity Limit: Enforces a maximum of 4 active horses per stable building unit at all times.\n"
               "4. Unassignment Effect: Releasing a stall resets current_trainer_id to NULL and transitions intake_status back to PENDING_STALL.",
               tone='amber')

# =========================================================================
# TAB 06: INVENTORY REPLENISHMENT & RECEIPT
# =========================================================================
def build_tab_06():
    p = DiagramPage(6, "Replenishment and receipt",
                    "FLOW 06 · INVENTORY PROCUREMENT, APPROVAL & STOCK RECEIPT",
                    "Controlled multi-party procurement: Staff requisition, Manager approval, Supplier fulfillment, and Physical receipt.")
    
    p.section(140, "STAGE 1: REQUISITION SUBMISSION & EXECUTIVE APPROVAL EVALUATION")
    p.start_node('p6_start', 50, 185, 'Start Request')
    p.action('p6_staff_detect', 220, 175, 'Detect Low Stock Level\nfor Essential Feed/Medicine', 'STABLE STAFF', kind='other')
    p.action('p6_staff_submit', 460, 175, 'Submit Replenishment Request\n(Item, Quantity, Reason)', 'STABLE STAFF', kind='other')
    p.action('p6_set_pending', 700, 175, 'Record REPLENISHMENT_REQUEST\nwith status="PENDING_APPROVAL"', 'SYSTEM', kind='sys')
    p.action('p6_mgr_review', 940, 175, 'Open Inventory Catalog &\nReview Proposal & Budget', 'CLUB MANAGER')
    p.decision('p6_mgr_decision', 1190, 175, 'Does Club Manager\nApprove Proposal?', 180, 75)
    p.action('p6_mgr_reject', 1175, 280, 'Enter Rejection Remarks:\nstatus = "REJECTED"', 'CLUB MANAGER', kind='cm')
    p.action('p6_mgr_approve', 1430, 175, 'Enter Approval Notes:\nstatus = "APPROVED"', 'CLUB MANAGER', kind='cm')
    p.end_node('p6_end_rejected', 1190, 385, 'Request Rejected', kind='err')
    
    p.edge('p6_start', 'p6_staff_detect')
    p.edge('p6_staff_detect', 'p6_staff_submit')
    p.edge('p6_staff_submit', 'p6_set_pending')
    p.edge('p6_set_pending', 'p6_mgr_review')
    p.edge('p6_mgr_review', 'p6_mgr_decision')
    p.edge('p6_mgr_decision', 'p6_mgr_reject', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p6_mgr_reject', 'p6_end_rejected', sp='b', tp='t')
    p.edge('p6_mgr_decision', 'p6_mgr_approve', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    
    p.section(460, "STAGE 2: GOODS ARRIVAL, PHYSICAL INSPECTION & ATOMIC INVENTORY STOCK-IN")
    p.decision('p6_check_mode', 1430, 485, 'Goods Received\nImmediately On-Site?', 180, 75)
    p.action('p6_await_delivery', 1190, 485, 'Wait for Vendor Shipment\n(Supplier Delivery Phase)', 'SYSTEM', kind='sys')
    p.action('p6_goods_arrive', 940, 485, 'Shipment Arrives at Stable;\nInspect Package & Expiry', 'STABLE STAFF', kind='other')
    p.action('p6_click_receive', 700, 485, 'Click "Confirm Receipt":\nPOST /replenishments/{id}/receive', 'STABLE STAFF', kind='other')
    p.decision('p6_check_idempotent', 460, 485, 'Has Request Already\nBeen Fulfilled?', 180, 75)
    p.error_box('p6_err_already', 460, 585, 'Blocked: Request Already\nFulfilled Once')
    p.action('p6_atomic_stock_in', 220, 485, 'Atomic DB Transaction:\nIncrement item stock_quantity', 'SYSTEM', kind='sys')
    p.action('p6_set_fulfilled', 220, 600, 'Set status = "FULFILLED"\nand received_at = NOW', 'SYSTEM', kind='sys')
    p.action('p6_audit_restock', 460, 600, 'Log Restock Transaction\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p6_end_restocked', 710, 615, 'Stock Available for Use', kind='ok')
    
    p.edge('p6_mgr_approve', 'p6_check_mode', sp='b', tp='t')
    p.edge('p6_check_mode', 'p6_click_receive', 'Yes', sp='b', tp='t', via=[(1520, 430), (805, 430)])
    p.edge('p6_check_mode', 'p6_await_delivery', 'No', sp='l', tp='r')
    p.edge('p6_await_delivery', 'p6_goods_arrive')
    p.edge('p6_goods_arrive', 'p6_click_receive')
    p.edge('p6_click_receive', 'p6_check_idempotent')
    p.edge('p6_check_idempotent', 'p6_err_already', 'Yes', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p6_check_idempotent', 'p6_atomic_stock_in', 'No', sp='l', tp='r', color=C['ok_stroke'])
    p.edge('p6_atomic_stock_in', 'p6_set_fulfilled', sp='b', tp='t')
    p.edge('p6_set_fulfilled', 'p6_audit_restock')
    p.edge('p6_audit_restock', 'p6_end_restocked')
    
    p.note_box("APPROVAL AND RECEIPT INTEGRITY",
               "1. Exclusive Approval Authority: Only users holding ROLE_CLUB_MANAGER are authorized to approve or reject replenishment requests.\n"
               "2. Double-Increment Protection: The receipt handler strictly verifies status == 'APPROVED' prior to incrementing stock_quantity, preventing duplicate stock booking.\n"
               "3. Financial Boundary: Goods receipt increases physical stock; it does NOT automatically book accounting expenses until stock is consumed in Flow 07.",
               tone='blue')

# =========================================================================
# TAB 07: INVENTORY USAGE & EXPENSE RECORDING
# =========================================================================
def build_tab_07():
    p = DiagramPage(7, "Inventory usage and costs",
                    "FLOW 07 · INVENTORY CONSUMPTION & GENERAL LEDGER EXPENSE INTEGRITY",
                    "Single transactional boundary: Physical stock deduction and financial expense booking must succeed together or roll back.")
    
    p.section(140, "STAGE 1: SELECT CONSUMPTION TARGET & STOCK AVAILABILITY VALIDATION")
    p.start_node('p7_start', 50, 185, 'Start Usage')
    p.action('p7_select_item', 220, 175, 'Select Item from Catalog\n(Feed, Supplement, Med)', 'STABLE STAFF', kind='other')
    p.action('p7_input_params', 470, 175, 'Enter Quantity Consumed,\nUnit Cost & Date', 'STABLE STAFF', kind='other')
    p.action('p7_select_target', 720, 175, 'Select Target: Specific Horse\nOR Specific Stable Unit', 'STABLE STAFF', kind='other')
    p.decision('p7_check_valid', 960, 175, 'Quantity > 0 and Target\nEntity Specified?', 180, 75)
    p.error_box('p7_err_valid', 960, 275, 'Validation Failed:\nMissing Target or Quantity')
    p.decision('p7_check_stock', 1200, 175, 'Available Stock >=\nRequested Quantity?', 180, 75)
    p.error_box('p7_err_stock', 1200, 275, 'Insufficient Inventory:\nCannot Exceed Stock')
    p.action('p7_begin_tx', 1440, 175, 'Begin Atomic Database\nTransaction (@Transactional)', 'SYSTEM', kind='sys')
    p.action('p7_lock_row', 1680, 175, 'Acquire Row-level Lock\non inventory_items Row', 'SYSTEM', kind='sys')
    
    p.edge('p7_start', 'p7_select_item')
    p.edge('p7_select_item', 'p7_input_params')
    p.edge('p7_input_params', 'p7_select_target')
    p.edge('p7_select_target', 'p7_check_valid')
    p.edge('p7_check_valid', 'p7_err_valid', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p7_check_valid', 'p7_check_stock', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p7_check_stock', 'p7_err_stock', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p7_check_stock', 'p7_begin_tx', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p7_begin_tx', 'p7_lock_row')
    
    p.section(420, "STAGE 2: ATOMIC EXECUTION: STOCK DECREMENT + USAGE LOG + FINANCIAL EXPENSE")
    p.action('p7_deduct_stock', 1680, 455, 'Deduct stock_quantity =\nstock_quantity - consumed', 'SYSTEM', kind='sys')
    p.action('p7_insert_usage', 1440, 455, 'Insert Record into\ndbo.inventory_usages', 'SYSTEM', kind='sys')
    p.action('p7_insert_expense', 1200, 455, 'Insert Record into dbo.financial_transactions\n(amount = quantity * unit_price)', 'SYSTEM', kind='sys')
    p.action('p7_link_tx', 960, 455, 'Set usage.financial_tx_id =\nfinancial_transactions.id', 'SYSTEM', kind='sys')
    p.action('p7_commit_tx', 720, 455, 'Commit Database Transaction\n(Both Tables Updated)', 'SYSTEM', kind='sys')
    p.action('p7_refresh_grid', 470, 455, 'Refresh Real-time Stock\nGrid & Financial Summary', 'CLUB MANAGER')
    p.end_node('p7_end_usage', 220, 470, 'Usage & Expense Logged', kind='ok')
    
    p.edge('p7_lock_row', 'p7_deduct_stock', sp='b', tp='t')
    p.edge('p7_deduct_stock', 'p7_insert_usage')
    p.edge('p7_insert_usage', 'p7_insert_expense')
    p.edge('p7_insert_expense', 'p7_link_tx')
    p.edge('p7_link_tx', 'p7_commit_tx')
    p.edge('p7_commit_tx', 'p7_refresh_grid')
    p.edge('p7_refresh_grid', 'p7_end_usage')
    
    p.note_box("TRANSACTIONAL BOUNDARY & EXPENSE RECONCILIATION",
               "1. Atomic Consistency: Inventory subtraction and expense booking occur inside a single Spring database transaction; if either fails, both revert.\n"
               "2. Target Entity Rules: Medical supplies must link to a specific horse; facility items (straw, sawdust) link to a stable unit.\n"
               "3. Real Money vs Expense: This record logs operational consumption cost in VND; it does not automatically debit external bank accounts.",
               tone='blue')

print("Tabs 04-07 configured")

# =========================================================================
# TAB 08: RACE MANAGEMENT & CONSENT TRIAD
# =========================================================================
def build_tab_08():
    p = DiagramPage(8, "Race management",
                    "FLOW 08 · RACE EVENT LIFECYCLE & CONSENT TRIAD VERIFICATION",
                    "Mandatory tripartite consensus: Trainer nomination, Owner consent, and Manager official gate confirmation.")
    
    p.section(140, "STAGE 1: RACE EVENT CREATION & HEAD TRAINER HORSE NOMINATION")
    p.start_node('p8_start', 50, 185, 'Start Race')
    p.action('p8_mgr_create_event', 220, 175, 'Create Race Event:\nName, Track, Distance, Class', 'CLUB MANAGER')
    p.action('p8_input_deadlines', 470, 175, 'Specify Race Date, Entry\nDeadline, Age & Weight Limits', 'CLUB MANAGER')
    p.decision('p8_check_dates', 720, 175, 'Race Date in Future &\nDeadline < Race Date?', 180, 75)
    p.error_box('p8_err_dates', 720, 275, 'Invalid Dates:\nDeadline must precede Race')
    p.action('p8_publish_race', 950, 175, 'Publish Event in dbo.race_events\n(is_active=1, completed_at=NULL)', 'SYSTEM', kind='sys')
    p.action('p8_trainer_inspect', 1190, 175, 'Inspect Stabled Horses for\nRace Fitness & Form (FR07)', 'HEAD TRAINER', kind='other')
    p.action('p8_trainer_nominate', 1430, 175, 'Submit Nomination:\nPOST /races/{id}/registrations', 'HEAD TRAINER', kind='other')
    p.action('p8_set_submitted', 1670, 175, 'Record RACE_REGISTRATION\nwith status = "SUBMITTED"', 'SYSTEM', kind='sys')
    
    p.edge('p8_start', 'p8_mgr_create_event')
    p.edge('p8_mgr_create_event', 'p8_input_deadlines')
    p.edge('p8_input_deadlines', 'p8_check_dates')
    p.edge('p8_check_dates', 'p8_err_dates', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p8_check_dates', 'p8_publish_race', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p8_publish_race', 'p8_trainer_inspect')
    p.edge('p8_trainer_inspect', 'p8_trainer_nominate')
    p.edge('p8_trainer_nominate', 'p8_set_submitted')
    
    p.section(420, "STAGE 2: OWNER CONSENT, MANAGER CONFIRMATION & OFFICIAL STARTING ROSTER")
    p.action('p8_notify_owner', 1670, 455, 'Notify Horse Owner of\nNomination & Entry Fee', 'SYSTEM', kind='sys')
    p.decision('p8_owner_decision', 1430, 455, 'Consent Triad: Does\nHorse Owner Consent?', 180, 75)
    p.action('p8_owner_reject', 1430, 565, 'Owner Declines Entry:\nstatus = "REJECT"', 'HORSE OWNER', kind='other')
    p.action('p8_owner_approve', 1190, 455, 'Owner Approves Entry:\nstatus = "APPROVE"', 'HORSE OWNER', kind='other')
    p.action('p8_mgr_review_list', 950, 455, 'Manager Reviews Entries\nApproved by Owners', 'CLUB MANAGER')
    p.decision('p8_check_trigger_race', 710, 455, 'DB Trigger Guard:\nStatus == "APPROVE" &\nEvent Not Completed?', 190, 75)
    p.error_box('p8_err_trigger_race', 710, 565, 'Trigger Blocked:\nCannot Confirm Unapproved Entry')
    p.action('p8_mgr_confirm', 460, 455, 'Click "Confirm Entry":\nPOST /confirm', 'CLUB MANAGER')
    p.action('p8_set_confirmed', 220, 455, 'Set status = "CONFIRMED":\nHorse Placed on Roster', 'SYSTEM', kind='sys')
    p.end_node('p8_end_rejected', 1430, 670, 'Entry Withdrawn', kind='err')
    
    p.edge('p8_set_submitted', 'p8_notify_owner', sp='b', tp='t')
    p.edge('p8_notify_owner', 'p8_owner_decision', sp='l', tp='r')
    p.edge('p8_owner_decision', 'p8_owner_reject', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p8_owner_reject', 'p8_end_rejected', sp='b', tp='t')
    p.edge('p8_owner_decision', 'p8_owner_approve', 'Yes', sp='l', tp='r', color=C['ok_stroke'])
    p.edge('p8_owner_approve', 'p8_mgr_review_list')
    p.edge('p8_mgr_review_list', 'p8_check_trigger_race')
    p.edge('p8_check_trigger_race', 'p8_err_trigger_race', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p8_check_trigger_race', 'p8_mgr_confirm', 'Yes', sp='l', tp='r', color=C['ok_stroke'])
    p.edge('p8_mgr_confirm', 'p8_set_confirmed')
    
    p.section(680, "STAGE 3: RACE EXECUTION, TIMING TELEMETRY & EVENT FINALIZATION")
    p.action('p8_race_day_held', 220, 720, 'Race Day: Event Takes Place\non Track; Results Recorded', 'ALL ROLES', kind='other')
    p.action('p8_mgr_complete', 460, 720, 'Click "Complete Race Event"\non / after Race Date', 'CLUB MANAGER')
    p.action('p8_set_completed', 710, 720, 'Set completed_at = NOW\nand Lock Race Registrations', 'SYSTEM', kind='sys')
    p.end_node('p8_end_race', 950, 735, 'Race Finalized', kind='ok')
    
    p.edge('p8_set_confirmed', 'p8_race_day_held', sp='b', tp='t')
    p.edge('p8_race_day_held', 'p8_mgr_complete')
    p.edge('p8_mgr_complete', 'p8_set_completed')
    p.edge('p8_set_completed', 'p8_end_race')
    
    p.note_box("DATABASE TRIGGER GUARD: TR_RACE_REGISTRATION_STATUS_GUARD",
               "1. Tripartite Consent Rule: Trainer initiates (SUBMITTED) -> Owner authorizes (APPROVE) -> Club Manager confirms (CONFIRMED).\n"
               "2. State Progression: Direct transition from SUBMITTED to CONFIRMED by manager is strictly rejected by Trigger 53001.\n"
               "3. Irrevocability: Once an entry is CONFIRMED, it cannot be withdrawn without formal steward intervention; no edits after race completion.",
               tone='amber')

# =========================================================================
# TAB 09: FINANCE & REPORTS
# =========================================================================
def build_tab_09():
    p = DiagramPage(9, "Finance and reports",
                    "FLOW 09 · GENERAL LEDGER MANAGEMENT & STAKEHOLDER FINANCIAL REPORTING",
                    "Direct income / expense booking, anti-double-counting controls, MoM variance, and certified statement export.")
    
    p.section(140, "SUB-FLOW A: DIRECT FINANCIAL LEDGER TRANSACTION RECORDING")
    p.start_node('p9_start_ledger', 50, 185, 'Start Finance')
    p.action('p9_open_finance', 220, 175, 'Open Finance Reports\n(/admin/finance-reports)', 'CLUB MANAGER')
    p.action('p9_click_add_tx', 470, 175, 'Click "Add Transaction"\nfor Offline Revenue/Expense', 'CLUB MANAGER')
    p.action('p9_input_tx_fields', 720, 175, 'Enter Type (INCOME/EXPENSE),\nCategory, Amount VND, Date', 'CLUB MANAGER')
    p.decision('p9_check_anti_double', 960, 175, 'Is Entry Independent?\n(Ensure not duplicating PayOS\nor Stock Usage Expenses)', 190, 75)
    p.error_box('p9_err_double', 960, 275, 'Blocked: Automated Entries\nMust Not Be Duplicated')
    p.decision('p9_check_valid_tx', 1200, 175, 'Amount > 0 and Account\nCategory Supported?', 180, 75)
    p.error_box('p9_err_invalid_tx', 1200, 275, 'Validation Failed:\nInvalid Amount or Category')
    p.action('p9_insert_ledger_db', 1430, 175, 'Insert Record into dbo.financial_transactions', 'SYSTEM', kind='sys')
    p.action('p9_audit_finance', 1670, 175, 'Log Financial Operation\nin dbo.audit_logs', 'SYSTEM', kind='sys')
    p.end_node('p9_end_ledger', 1695, 275, 'Transaction Appended', kind='ok')
    
    p.edge('p9_start_ledger', 'p9_open_finance')
    p.edge('p9_open_finance', 'p9_click_add_tx')
    p.edge('p9_click_add_tx', 'p9_input_tx_fields')
    p.edge('p9_input_tx_fields', 'p9_check_anti_double')
    p.edge('p9_check_anti_double', 'p9_err_double', 'Duplicated', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p9_check_anti_double', 'p9_check_valid_tx', 'Unique', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p9_check_valid_tx', 'p9_err_invalid_tx', 'No', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p9_check_valid_tx', 'p9_insert_ledger_db', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p9_insert_ledger_db', 'p9_audit_finance')
    p.edge('p9_audit_finance', 'p9_end_ledger', sp='b', tp='t')
    
    p.section(420, "SUB-FLOW B: MONTH-OVER-MONTH (MoM) VARIANCE ANALYSIS & CERTIFIED EXPORT")
    p.start_node('p9_start_report', 50, 465, 'Start Reports')
    p.action('p9_select_period', 220, 455, 'Select Accounting Month,\nYear & Category Filter', 'CLUB MANAGER')
    p.action('p9_aggregate_cash', 470, 455, 'Aggregate Total Revenue,\nTotal Expenses & Net VND', 'SYSTEM', kind='sys')
    p.action('p9_select_benchmark', 720, 455, 'Select Comparison Benchmark\nMonth for MoM Analysis', 'CLUB MANAGER')
    p.action('p9_calc_variance', 960, 455, 'Compute Variance % &\nCategory Breakdown Trends', 'SYSTEM', kind='sys')
    p.action('p9_review_owner_stmt', 1200, 455, 'Inspect Horse Owner Daily\nBilling Statements (06:00 Job)', 'CLUB MANAGER')
    p.action('p9_click_export', 1430, 455, 'Click "Export CSV" / JSON\nfor External Tax & Audit', 'CLUB MANAGER')
    p.action('p9_stream_csv', 1670, 455, 'Stream UTF-8 BOM CSV File\nwith Full Transaction History', 'SYSTEM', kind='sys')
    p.end_node('p9_end_report', 1695, 555, 'Financial Package Exported', kind='ok')
    
    p.edge('p9_start_report', 'p9_select_period')
    p.edge('p9_select_period', 'p9_aggregate_cash')
    p.edge('p9_aggregate_cash', 'p9_select_benchmark')
    p.edge('p9_select_benchmark', 'p9_calc_variance')
    p.edge('p9_calc_variance', 'p9_review_owner_stmt')
    p.edge('p9_review_owner_stmt', 'p9_click_export')
    p.edge('p9_click_export', 'p9_stream_csv')
    p.edge('p9_stream_csv', 'p9_end_report', sp='b', tp='t')
    
    p.note_box("FINANCIAL ACCOUNTING PRINCIPLES & ANTI-DUPLICATION",
               "1. Append-Only General Ledger: The financial_transactions table is strictly additive. Adjustments are logged as reversing or compensating entries.\n"
               "2. Double-Count Prevention: Automated transactions (PayOS webhook wallet top-ups, stock usage deductions) automatically generate ledger entries and must not be re-entered.\n"
               "3. Currency Standardization: All ledger entries are recorded in Vietnamese Dong (VND) without fractional currency units.",
               tone='blue')

# =========================================================================
# TAB 10: AUDIT LOGS (Immutable System Audit Trail)
# =========================================================================
def build_tab_10():
    p = DiagramPage(10, "Audit logs",
                    "FLOW 10 · FORENSIC AUDIT TRAIL & SYSTEM COMPLIANCE REVIEW",
                    "Database Trigger append-only immutability, serialized before/after JSON state diffs, and investigative discrepancy workflows.")
    
    p.section(140, "STAGE 1: SYSTEM-WIDE AUDIT INTERCEPTION & IMMUTABLE DATABASE STORAGE")
    p.start_node('p10_start_interceptor', 50, 185, 'System Event')
    p.action('p10_user_action', 220, 175, 'Any User Performs State-changing\nAction (Create/Update/Delete)', 'ALL ROLES', kind='other')
    p.action('p10_interceptor', 470, 175, 'Spring Audit Interceptor Captures\nTarget Method Execution', 'SYSTEM', kind='sys')
    p.action('p10_serialize_diff', 720, 175, 'Serialize old_values_json and\nnew_values_json State Diffs', 'SYSTEM', kind='sys')
    p.action('p10_capture_context', 960, 175, 'Capture Actor ID, IP Address,\nUser Agent & Timestamp', 'SYSTEM', kind='sys')
    p.action('p10_insert_log', 1200, 175, 'Execute INSERT into\ndbo.audit_logs Table', 'SYSTEM', kind='sys')
    p.decision('p10_check_trigger_audit', 1440, 175, 'DB Trigger tr_audit_logs_append_only:\nAny UPDATE or DELETE Attempted?', 200, 80)
    p.error_box('p10_abort_tamper', 1440, 285, 'Trigger 50001: Statement Aborted!\nAudit Log Table is Immutable')
    p.action('p10_stored_ok', 1680, 175, 'Record Stored Permanently\nin Immutable Legal Store', 'SYSTEM', kind='sys')
    
    p.edge('p10_start_interceptor', 'p10_user_action')
    p.edge('p10_user_action', 'p10_interceptor')
    p.edge('p10_interceptor', 'p10_serialize_diff')
    p.edge('p10_serialize_diff', 'p10_capture_context')
    p.edge('p10_capture_context', 'p10_insert_log')
    p.edge('p10_insert_log', 'p10_check_trigger_audit')
    p.edge('p10_check_trigger_audit', 'p10_abort_tamper', 'Tamper', sp='b', tp='t', color=C['err_stroke'])
    p.edge('p10_check_trigger_audit', 'p10_stored_ok', 'Append Only', sp='r', tp='l', color=C['ok_stroke'])
    
    p.section(440, "STAGE 2: FORENSIC INVESTIGATION, STATE DIFF INSPECTION & REMEDIATION")
    p.start_node('p10_start_forensic', 50, 485, 'Start Review')
    p.action('p10_open_audit_page', 220, 475, 'Open Audit Logs Portal\n(/admin/audit-logs)', 'CLUB MANAGER')
    p.action('p10_filter_logs', 470, 475, 'Filter by User ID, Module,\nAction Type & Date Range', 'CLUB MANAGER')
    p.action('p10_exec_query', 720, 475, 'Query dbo.audit_logs with\nIndex Pagination', 'SYSTEM', kind='sys')
    p.decision('p10_check_results', 960, 475, 'Matching Audit\nRecords Found?', 170, 75)
    p.error_box('p10_empty_view', 960, 580, 'Empty Result View:\nNo Matching Actions Logged')
    p.action('p10_inspect_diff', 1190, 475, 'Inspect JSON Diff Modal:\nCompare Before vs After Values', 'CLUB MANAGER')
    p.decision('p10_check_breach', 1430, 475, 'Policy Breach or Suspicious\nDiscrepancy Identified?', 180, 75)
    p.action('p10_verified_ok', 1415, 580, 'Verification Complete:\nOperational Integrity Confirmed', 'CLUB MANAGER', kind='ok')
    p.action('p10_remediation', 1670, 475, 'Identify Responsible User/IP &\nInitiate Remediation Workflow', 'CLUB MANAGER')
    p.action('p10_export_audit_csv', 1670, 600, 'Click "Export CSV" to Produce\nCertified Security Report', 'CLUB MANAGER')
    p.end_node('p10_end_audit', 1695, 715, 'Compliance Audit Finalized', kind='ok')
    
    p.edge('p10_start_forensic', 'p10_open_audit_page')
    p.edge('p10_open_audit_page', 'p10_filter_logs')
    p.edge('p10_filter_logs', 'p10_exec_query')
    p.edge('p10_exec_query', 'p10_check_results')
    p.edge('p10_check_results', 'p10_empty_view', 'No', sp='b', tp='t', color=C['dec_stroke'])
    p.edge('p10_check_results', 'p10_inspect_diff', 'Yes', sp='r', tp='l', color=C['ok_stroke'])
    p.edge('p10_inspect_diff', 'p10_check_breach')
    p.edge('p10_check_breach', 'p10_verified_ok', 'No', sp='b', tp='t', color=C['ok_stroke'])
    p.edge('p10_check_breach', 'p10_remediation', 'Yes', sp='r', tp='l', color=C['err_stroke'])
    p.edge('p10_remediation', 'p10_export_audit_csv', sp='b', tp='t')
    p.edge('p10_export_audit_csv', 'p10_end_audit', sp='b', tp='t')
    
    p.note_box("DATABASE TRIGGER GUARD: TR_AUDIT_LOGS_APPEND_ONLY",
               "1. Tamper-Proof Legal Protection: Database trigger tr_audit_logs_append_only blocks all UPDATE and DELETE operations unconditionally (Error 50001).\n"
               "2. State Reconstruction: Serialized JSON snapshots preserve old_values_json and new_values_json, enabling exact state diff reconstruction at any point in history.\n"
               "3. Investigation Scope: Audit log inspection is a verification and compliance tool; it does not substitute for active operational approval steps.",
               tone='amber')

def main():
    build_tab_00()
    build_tab_01()
    build_tab_02()
    build_tab_03()
    build_tab_04()
    build_tab_05()
    build_tab_06()
    build_tab_07()
    build_tab_08()
    build_tab_09()
    build_tab_10()
    
    mx = ET.Element('mxfile', {
        'host': 'app.diagrams.net',
        'modified': '2026-11-06T18:30:00.000Z',
        'agent': 'Antigravity RTMS',
        'version': '24.7.17',
        'type': 'device',
        'compressed': 'false'
    })
    for p in pages:
        mx.append(p.diagram)
        
    ET.indent(mx, space='  ')
    
    ET.ElementTree(mx).write(OUT_FILE, encoding='utf-8', xml_declaration=True)
    
    print(f"Successfully generated expanded English workflow with {len(pages)} pages!")
    print(f"Target: {OUT_FILE} ({OUT_FILE.stat().st_size} bytes)")

if __name__ == '__main__':
    main()
