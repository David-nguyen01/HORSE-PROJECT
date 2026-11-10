from pathlib import Path
import xml.etree.ElementTree as E
import html, json, math

OUT=Path(r'D:\HORSE-PROJECT\BUSINESS_DOCS\workflows')
TMP=Path(r'C:\Users\dinhd\AppData\Local\Temp\rtms-workflow-20261106')
W,H=1800,1120
C={'ink':'#172F3B','muted':'#607580','line':'#293E49','green':'#157A59','light':'#EAF6F0','blue':'#3277A5','bluelight':'#EAF3FA','amber':'#B87715','amberlight':'#FFF5DD','red':'#B74343','redlight':'#FCEEEE','gray':'#F2F5F7','border':'#D7E2E6'}
def esc(s): return html.escape(str(s),quote=True)
def fmt(n): return str(round(n,2))
pages=[]
class Page:
 def __init__(self,num,name,title,subtitle):
  self.num=num;self.name=name;self.title=title;self.items=[];self.nodes={};self.edges=[]
  self.diagram=E.Element('diagram',{'id':f'cm-{num:02}','name':f'{num:02} · {name}'})
  model=E.SubElement(self.diagram,'mxGraphModel',{'dx':str(W),'dy':str(H),'grid':'1','gridSize':'10','guides':'1','tooltips':'1','connect':'1','arrows':'1','fold':'1','page':'1','pageScale':'1','pageWidth':str(W),'pageHeight':str(H),'background':'#FFFFFF','math':'0','shadow':'0'})
  self.root=E.SubElement(model,'root');E.SubElement(self.root,'mxCell',id='0');E.SubElement(self.root,'mxCell',id='1',parent='0')
  self.count=1
  self.rect(50,42,7,82,C['green'],'none')
  self.text(76,35,1100,26,'STRIDE  /  RACEHORSE TRAINING MANAGEMENT SYSTEM',15,C['green'],True,'left')
  self.text(76,75,1510,60,title,40,C['ink'],True,'left')
  self.text(76,135,1640,48,subtitle,19,C['muted'],False,'left')
  self.text(1530,39,200,32,f'CLUB MANAGER   /   {num:02}',14,C['green'],True,'right')
  self.line([(60,195),(1740,195)],C['border'],1)
  self.text(65,1070,1000,24,'RTMS · Đối chiếu mã nguồn local 06/11/2026 · Nhánh develop · 63b9362',13,C['muted'],False,'left')
  self.text(1270,1070,470,24,f'WORKFLOW {num:02}  /  10   •   CHỈNH SỬA ĐƯỢC TRONG DRAW.IO',12,C['muted'],False,'right')
  pages.append(self)
 def uid(self):
  self.count+=1;return f'p{self.num}-{self.count}'
 def cell(self,id,value,style,x,y,w,h,parent='1'):
  a={'id':id,'value':value,'style':style,'parent':parent,'vertex':'1'}
  c=E.SubElement(self.root,'mxCell',a);E.SubElement(c,'mxGeometry',{'x':fmt(x),'y':fmt(y),'width':fmt(w),'height':fmt(h),'as':'geometry'});return c
 def rect(self,x,y,w,h,fill,stroke=None,r=0,parent='1',offset=(0,0),shape='rect',sw=1.5):
  stroke=stroke or C['border']
  self.cell(self.uid(),'',
   f'shape={shape};rounded={1 if r else 0};arcSize=12;html=0;fillColor={fill};strokeColor={stroke};strokeWidth={sw};',
   x,y,w,h,parent)
  self.items.append(dict(kind=shape,x=x+offset[0],y=y+offset[1],w=w,h=h,fill=fill,stroke=stroke,r=r,sw=sw))
 def text(self,x,y,w,h,s,size=20,color=None,bold=False,align='center',parent='1',offset=(0,0)):
  self.cell(self.uid(),s,f'text;html=0;whiteSpace=wrap;overflow=hidden;fillColor=none;strokeColor=none;fontFamily=Arial;fontSize={size};fontColor={color or C["ink"]};fontStyle={1 if bold else 0};align={align};verticalAlign=middle;spacing=0;',x,y,w,h,parent)
  self.items.append(dict(kind='text',x=x+offset[0],y=y+offset[1],w=w,h=h,text=s,size=size,color=color or C['ink'],bold=bold,align=align))
 def line(self,pts,color=None,sw=2.5,parent='1',offset=(0,0),arrow=False,dashed=False):
  id=self.uid();sty=f'edgeStyle=none;html=0;rounded=0;endArrow={"block" if arrow else "none"};endFill=1;strokeColor={color or C["line"]};strokeWidth={sw};dashed={int(dashed)};'
  c=E.SubElement(self.root,'mxCell',{'id':id,'style':sty,'edge':'1','parent':parent})
  g=E.SubElement(c,'mxGeometry',{'relative':'1','as':'geometry'})
  for p,a in [(pts[0],'sourcePoint'),(pts[-1],'targetPoint')]: E.SubElement(g,'mxPoint',{'x':fmt(p[0]),'y':fmt(p[1]),'as':a})
  if len(pts)>2:
   arr=E.SubElement(g,'Array',{'as':'points'})
   for p in pts[1:-1]: E.SubElement(arr,'mxPoint',{'x':fmt(p[0]),'y':fmt(p[1])})
  self.items.append(dict(kind='line',pts=[(x+offset[0],y+offset[1]) for x,y in pts],color=color or C['line'],sw=sw,arrow=arrow,dashed=dashed))
 def icon(self,name,x,y,size,parent='1',offset=(0,0),color=None):
  k=size/64;col=color or C['green']
  def r(a,b,w,h,fill='none',shape='rect',rad=0):
   self.rect(x+a*k,y+b*k,w*k,h*k,fill,col,rad,parent,offset,shape,2)
  def l(ps,sw=2.5): self.line([(x+a*k,y+b*k) for a,b in ps],col,sw,parent,offset)
  if name=='person':
   r(19,2,26,26,'#F1C7A5','ellipse');r(5,34,54,28,col,'rect',8);l([(27,35),(32,47),(37,35)])
  elif name=='horse':
   l([(12,58),(17,35),(26,20),(26,6),(37,17),(48,20),(57,38),(51,44),(36,40),(30,58),(12,58)],3);r(41,26,3,3,col,'ellipse')
  elif name=='shield':
   l([(32,4),(55,13),(52,38),(43,51),(32,60),(21,51),(12,38),(9,13),(32,4)],3);l([(20,31),(29,40),(45,23)],3)
  elif name=='building':
   l([(3,26),(32,5),(61,26)],3);r(10,26,44,34);r(25,39,14,21);r(15,32,6,7);r(43,32,6,7)
  elif name=='box':
   r(7,18,50,40);l([(7,18),(32,5),(57,18),(32,31),(7,18)]);l([(32,31),(32,58)]);l([(20,12),(46,25)])
  elif name=='chart':
   l([(7,6),(7,57),(60,57)]);r(17,32,9,18,col);r(33,21,9,29,col);r(49,9,9,41,col)
  elif name=='trophy':
   l([(17,7),(47,7),(44,31),(32,42),(20,31),(17,7)],3);l([(17,13),(6,13),(6,28),(20,33)]);l([(47,13),(58,13),(58,28),(44,33)]);l([(32,42),(32,57),(20,57),(44,57)],3)
  elif name=='screen':
   r(4,5,56,40,shape='rect',rad=5);l([(24,45),(24,55),(40,55),(40,45)]);l([(16,58),(48,58)]);l([(13,31),(25,24),(34,29),(50,15)])
  elif name=='lock':
   r(11,27,42,31,shape='rect',rad=4);l([(20,27),(20,14),(25,5),(39,5),(44,14),(44,27)],3);r(29,37,6,6,col,'ellipse');l([(32,42),(32,49)],3)
  elif name=='clock':
   r(6,6,52,52,shape='ellipse');l([(32,16),(32,33),(45,40)],3)
  elif name=='check':
   r(2,2,60,60,col,'ellipse');self.line([(x+16*k,y+32*k),(x+28*k,y+44*k),(x+49*k,y+21*k)],'#FFFFFF',4,parent,offset)
  elif name=='cross':
   r(2,2,60,60,col,'ellipse')
   self.line([(x+21*k,y+21*k),(x+43*k,y+43*k)],'#FFFFFF',4,parent,offset);self.line([(x+43*k,y+21*k),(x+21*k,y+43*k)],'#FFFFFF',4,parent,offset)
  elif name=='medical':
   r(6,13,52,43,shape='rect',rad=5);l([(23,13),(23,5),(41,5),(41,13)]);l([(32,24),(32,45)],5);l([(22,34),(42,34)],5)
  else:
   r(12,4,40,56,shape='rect',rad=4);l([(21,18),(42,18)]);l([(21,29),(44,29)]);l([(21,40),(40,40)]);l([(21,50),(35,50)])
 def node(self,id,x,y,title,role='CLUB MANAGER',icon='doc',kind='action',w=240,h=180):
  col=C['green'] if role=='CLUB MANAGER' else C['blue'] if role=='HỆ THỐNG' else C['muted']
  if kind=='success':col=C['green'];icon='check'
  if kind in ['error','wait']:col=C['red'] if kind=='error' else C['amber'];icon='cross' if kind=='error' else 'clock'
  group=E.SubElement(self.root,'mxCell',{'id':id,'value':'','style':'group;','parent':'1','vertex':'1','connectable':'1'})
  E.SubElement(group,'mxGeometry',{'x':str(x),'y':str(y),'width':str(w),'height':str(h),'as':'geometry'})
  off=(x,y)
  if kind=='decision':
   self.rect(0,0,w,h,C['amberlight'],C['amber'],0,id,off,'rhombus')
   self.text(20,28,w-40,h-56,title,17,C['ink'],True,parent=id,offset=off)
  else:
   fill=C['light'] if kind=='success' else C['redlight'] if kind=='error' else C['amberlight'] if kind=='wait' else '#FFFFFF'
   self.rect(0,0,w,h,fill,C['border'],15,id,off)
   self.text(10,8,w-20,20,role,12,col,True,parent=id,offset=off)
   self.icon(icon,(w-48)/2,35,48,id,off,col)
   self.text(5,94,w-10,h-100,title,19,C['ink'],True,parent=id,offset=off)
  self.nodes[id]=(x,y,w,h)
  return id
 def edge(self,s,t,label='',sp='r',tp='l',via=None,color=None,dashed=False,labelpos=None):
  port={'r':(1,.5),'l':(0,.5),'b':(.5,1),'t':(.5,0)}
  sx,sy,sw,sh=self.nodes[s];tx,ty,tw,th=self.nodes[t];a,b=port[sp],port[tp]
  pts=[(sx+sw*a[0],sy+sh*a[1])]+(via or [])+[(tx+tw*b[0],ty+th*b[1])]
  col=color or C['line']
  sty=f'edgeStyle=none;rounded=1;html=0;endArrow=block;endFill=1;strokeColor={col};strokeWidth=2.3;dashed={int(dashed)};exitX={a[0]};exitY={a[1]};exitDx=0;exitDy=0;exitPerimeter=0;entryX={b[0]};entryY={b[1]};entryDx=0;entryDy=0;entryPerimeter=0;'
  c=E.SubElement(self.root,'mxCell',{'id':self.uid(),'value':'','edge':'1','parent':'1','source':s,'target':t,'style':sty})
  g=E.SubElement(c,'mxGeometry',{'relative':'1','as':'geometry'})
  if via:
   arr=E.SubElement(g,'Array',{'as':'points'})
   for x,y in via:E.SubElement(arr,'mxPoint',{'x':fmt(x),'y':fmt(y)})
  self.items.append(dict(kind='line',pts=pts,color=col,sw=2.3,arrow=True,dashed=dashed))
  if label:
   lx,ly,lw,lh=labelpos if labelpos else ((pts[0][0]+pts[-1][0])/2-51,(pts[0][1]+pts[-1][1])/2-53,102,42)
   self.rect(lx,ly,lw,lh,'#FFFFFF','none',0)
   self.text(lx,ly,lw,lh,label,15,col,True)
  self.edges.append((s,t,label))
 def note(self,title,body,x=60,y=930,w=1680,h=105,tone='blue'):
  col=C['blue'] if tone=='blue' else C['amber'] if tone=='amber' else C['green']
  fill=C['bluelight'] if tone=='blue' else C['amberlight'] if tone=='amber' else C['light']
  self.rect(x,y,w,h,fill,'none',12)
  self.text(x+22,y+10,w-44,27,title,17,col,True,'left')
  self.text(x+22,y+42,w-44,h-48,body,17,C['ink'],False,'left')
 def section(self,y,s):
  self.text(65,y,1660,32,s,17,C['green'],True,'left')
 def small(self,id,x,y,title,kind='error',w=240,h=76):
  col=C['red'] if kind=='error' else C['amber'] if kind=='wait' else C['green']
  fill=C['redlight'] if kind=='error' else C['amberlight'] if kind=='wait' else C['light']
  self.cell(id,title,f'rounded=1;arcSize=15;whiteSpace=wrap;html=0;fontFamily=Arial;fontSize=18;fontStyle=1;fontColor={col};fillColor={fill};strokeColor={col};spacing=8;',x,y,w,h)
  self.items.append(dict(kind='rect',x=x,y=y,w=w,h=h,fill=fill,stroke=col,r=10,sw=1.5))
  self.items.append(dict(kind='text',x=x+8,y=y+5,w=w-16,h=h-10,text=title,size=18,color=col,bold=True,align='center'))
  self.nodes[id]=(x,y,w,h)
 def render(self):
  out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="white"/>']
  for i in self.items:
   k=i['kind']
   if k=='text':
    lines=i['text'].split('\n');fs=i['size'];lh=fs*1.23
    anchor={'left':'start','right':'end','center':'middle'}[i['align']]
    x=i['x'] if anchor=='start' else i['x']+i['w'] if anchor=='end' else i['x']+i['w']/2
    y=i['y']+i['h']/2-(len(lines)-1)*lh/2+fs*.34
    out.append(f'<text x="{x}" y="{y}" font-family="Arial, sans-serif" font-size="{fs}" fill="{i["color"]}" font-weight="{700 if i["bold"] else 400}" text-anchor="{anchor}">')
    for j,s in enumerate(lines):out.append(f'<tspan x="{x}" y="{y+j*lh}">{esc(s)}</tspan>')
    out.append('</text>')
   elif k=='line':
    pts=i['pts'];out.append(f'<polyline points="{" ".join(f"{x},{y}" for x,y in pts)}" fill="none" stroke="{i["color"]}" stroke-width="{i["sw"]}" stroke-linecap="round" stroke-linejoin="round"'+(' stroke-dasharray="7 5"' if i.get('dashed') else '')+'/>')
    if i.get('arrow'):
     (a,b),(x,y)=pts[-2:];ang=math.atan2(y-b,x-a);le=10
     poly=[(x,y),(x-le*math.cos(ang)+5*math.sin(ang),y-le*math.sin(ang)-5*math.cos(ang)),(x-le*math.cos(ang)-5*math.sin(ang),y-le*math.sin(ang)+5*math.cos(ang))]
     out.append(f'<polygon points="{" ".join(f"{x},{y}" for x,y in poly)}" fill="{i["color"]}"/>')
   elif k=='ellipse':out.append(f'<ellipse cx="{i["x"]+i["w"]/2}" cy="{i["y"]+i["h"]/2}" rx="{i["w"]/2}" ry="{i["h"]/2}" fill="{i["fill"]}" stroke="{i["stroke"]}" stroke-width="{i["sw"]}"/>')
   elif k=='rhombus':
    x,y,w,h=i['x'],i['y'],i['w'],i['h']
    out.append(f'<polygon points="{x+w/2},{y} {x+w},{y+h/2} {x+w/2},{y+h} {x},{y+h/2}" fill="{i["fill"]}" stroke="{i["stroke"]}" stroke-width="{i["sw"]}"/>')
   else:out.append(f'<rect x="{i["x"]}" y="{i["y"]}" width="{i["w"]}" height="{i["h"]}" rx="{i["r"]}" fill="{i["fill"]}" stroke="{i["stroke"]}" stroke-width="{i["sw"]}"/>')
  out.append('</svg>');return ''.join(out)

X=[60,420,780,1140,1500];Y=250;Z=650
def n(p,i,id,title,role='CLUB MANAGER',icon='doc',kind='action',y=Y):return p.node(id,X[i],y,title,role,icon,kind)
def chain(p,*ids):
 for a,b in zip(ids,ids[1:]):p.edge(a,b)
def rejection(p,source,id='err',text='Từ chối thao tác\nHiển thị lý do',y=480):
 x=p.nodes[source][0];p.small(id,x,y,text)
 p.edge(source,id,'Không',sp='b',tp='t',color=C['red'],labelpos=(x+130,y-49,90,30))
# 00
p=Page(0,'Tổng quan','MAIN FLOW · CLUB MANAGER','Quản lý Câu lạc bộ là admin nghiệp vụ. Chọn từng nhánh để xem luồng thao tác và điều kiện xử lý.')
n(p,0,'cm','Quản lý Câu lạc bộ',icon='person')
n(p,1,'login','Nhập tài khoản\nvà mật khẩu',icon='lock')
n(p,2,'valid','Xác thực đúng\nvà có role\nCLUB_MANAGER?',kind='decision')
n(p,3,'dash','Mở Tổng quan\nquản trị',icon='screen',kind='success')
n(p,4,'logout','Đăng xuất\nThu hồi phiên',icon='lock')
chain(p,'cm','login','valid');p.edge('valid','dash','Có');p.edge('dash','logout','Kết thúc')
rejection(p,'valid',text='Không vào quản trị\nKiểm tra đăng nhập',y=475)
p.section(580,'TỪ TỔNG QUAN → CHỌN MỘT NHÓM CÔNG VIỆC')
for id,x,title,icon in [('access',60,'01 · Tài khoản\n02 · Vai trò & quyền','shield'),('horse',500,'03 · Hồ sơ & bảo lãnh\n04–05 · Tiếp nhận, chuồng','horse'),('operate',940,'06–07 · Vật tư & kho\n08 · Quản lý giải đua','box'),('monitor',1380,'09 · Tài chính, báo cáo\n10 · Nhật ký hệ thống','chart')]:
 p.node(id,x,670,title,icon=icon,w=360,h=185)
 # shared branch line; only arrowhead at destination
 p.edge('dash',id,sp='b',tp='t',via=[(1260,620),(x+180,620)])
p.note('CÁCH ĐỌC SƠ ĐỒ','Mỗi trang là một workflow độc lập. Mũi tên liền: bước xử lý; nét đứt: liên hệ / thao tác tùy chọn.\nMàu xanh lá: Club Manager hoặc thành công · Xanh dương: hệ thống · Xám: vai trò phối hợp · Vàng: điều kiện · Đỏ: bị chặn.')
# 01
p=Page(1,'Tài khoản','FLOW 01 · QUẢN LÝ TÀI KHOẢN','Tạo và cập nhật tài khoản; quản lý trạng thái, mật khẩu và phiên đăng nhập.')
p.section(205,'A. TẠO / SỬA THÔNG TIN')
n(p,0,'actor','Mở danh sách\nTài khoản',icon='person')
n(p,1,'input','Tạo mới hoặc\nsửa tên, email',icon='doc')
n(p,2,'check','Dữ liệu hợp lệ?\nTên / email\nkhông trùng?',kind='decision')
n(p,3,'save','Lưu tài khoản\nMã hóa mật khẩu','HỆ THỐNG','lock')
n(p,4,'done','Hiển thị kết quả\nvà tải lại danh sách',kind='success')
chain(p,'actor','input','check');p.edge('check','save','Có');p.edge('save','done')
rejection(p,'check',text='Chỉ rõ trường sai\nSửa rồi gửi lại')
p.section(590,'B. KHÓA / MỞ KHÓA / ĐẶT LẠI MẬT KHẨU / THU HỒI PHIÊN')
n(p,0,'pick','Chọn tài khoản\nvà thao tác',icon='person',y=Z)
n(p,1,'guard','Cho phép thao tác?\nNếu khóa: kiểm tra\nnhiệm vụ đang giữ',kind='decision',y=Z)
n(p,2,'apply','Xử lý yêu cầu\ntrạng thái / mật khẩu /\nphiên đăng nhập','HỆ THỐNG','lock',y=Z)
n(p,3,'sessions','Thu hồi phiên khi\nkhóa / reset / yêu cầu','HỆ THỐNG','shield',y=Z)
n(p,4,'audit','Thông báo kết quả\nGhi nhật ký',kind='success',y=Z)
chain(p,'pick','guard');p.edge('guard','apply','Có');chain(p,'apply','sessions','audit')
p.small('blocked',420,855,'Không: báo lý do cần bàn giao',w=390,h=52)
p.edge('guard','blocked',sp='b',tp='t',color=C['red'])
p.note('RÀNG BUỘC KHI KHÓA','Không tự khóa tài khoản; phải còn ít nhất một Club Manager hoạt động; không còn sở hữu / phụ trách ngựa,\ngiữ ca khám hoặc nhiệm vụ chăm sóc chưa xong. Mở khóa không khôi phục các phiên đã bị thu hồi.')
# 02
p=Page(2,'Vai trò và ma trận quyền','FLOW 02 · ĐỔI ROLE & PHÂN QUYỀN','Phân biệt đổi vai trò của một tài khoản với bật / tắt quyền của cả một vai trò.')
p.section(205,'A. ĐỔI VAI TRÒ CỦA TÀI KHOẢN')
n(p,0,'actor','Chọn tài khoản\ncủa người khác',icon='person')
n(p,1,'role','Chọn đúng 1 role\ntrong 5 role cố định',icon='shield')
n(p,2,'guard','Chưa có dữ liệu\nnghiệp vụ / phân công\nvà đủ điều kiện?',kind='decision')
n(p,3,'update','Lưu role mới\nThu hồi các phiên','HỆ THỐNG','lock')
n(p,4,'done','Vai trò mới\nđược áp dụng',kind='success')
chain(p,'actor','role','guard');p.edge('guard','update','Có');p.edge('update','done')
rejection(p,'guard',text='Không đổi role\nDùng tài khoản mới')
p.section(590,'B. BẬT / TẮT QUYỀN CỦA ROLE')
n(p,0,'matrix','Mở Ma trận quyền\nChọn role cần sửa',icon='shield',y=Z)
n(p,1,'toggle','Chọn phân hệ\nvà quyền cần đổi',icon='doc',y=Z)
n(p,2,'valid','Role khác admin?\nCặp phân hệ / quyền\nđược hỗ trợ?',kind='decision',y=Z)
n(p,3,'save','Lưu VIEW / EDIT /\nAPPROVE / CLAIM','HỆ THỐNG','shield',y=Z)
n(p,4,'effect','Có hiệu lực ở\nrequest tiếp theo',kind='success',y=Z)
chain(p,'matrix','toggle','valid');p.edge('valid','save','Có');p.edge('save','effect')
p.small('rejectmatrix',780,855,'Không: giữ nguyên quyền hiện tại',w=390,h=52);p.edge('valid','rejectmatrix',sp='b',tp='t',color=C['red'])
p.note('KHÔNG LÀM MẤT TRÁCH NHIỆM ĐÃ GHI NHẬN','Tài khoản đang / từng sở hữu ngựa hoặc đã có nghiệp vụ không được đổi role. Giữ nguyên role cũ là thao tác không thay đổi.\nKhông tự đổi role; không mất admin cuối cùng; không sửa quyền CLUB_MANAGER. Quyền động vẫn chịu ràng buộc nghiệp vụ ở API.')
# 03
p=Page(3,'Hồ sơ và bảo lãnh','FLOW 03 · HỒ SƠ NGỰA & BẢO LÃNH','Club Manager xem toàn bộ ngựa, kể cả chưa thanh toán; điều kiện sử dụng dịch vụ được kiểm tra riêng.')
p.section(205,'A. QUẢN TRỊ HỒ SƠ')
n(p,0,'list','Xem / tìm kiếm\nmọi hồ sơ ngựa',icon='horse')
n(p,1,'edit','Tạo thay chủ hoặc\nsửa hồ sơ, phả hệ',icon='doc')
n(p,2,'valid','Thông tin hợp lệ?\nMã / chip không trùng\nSở hữu, phả hệ đúng?',kind='decision')
n(p,3,'save','Lưu hồ sơ\nvà tệp đính kèm','HỆ THỐNG','doc')
n(p,4,'done','Tra cứu hồ sơ\nvà lịch sử',kind='success')
chain(p,'list','edit','valid');p.edge('valid','save','Có');p.edge('save','done');rejection(p,'valid',text='Không lưu dữ liệu sai\nSửa rồi gửi lại')
p.section(590,'B. XÉT BẢO LÃNH DỊCH VỤ CHO NGỰA')
n(p,0,'pick','Chọn ngựa chưa trả\nhoặc đã hết hạn phí',icon='horse',y=Z)
n(p,1,'review','Manager quyết định\nbảo lãnh dịch vụ?',kind='decision',y=Z)
n(p,2,'override','Bật bảo lãnh\nGhi nhận lý do',icon='shield',y=Z)
n(p,3,'allow','Cho phép vào\nquy trình tiếp nhận','HỆ THỐNG','screen',y=Z)
n(p,4,'flow','Chuyển Flow 04\nStaff → Vet → Staff',kind='success',y=Z)
chain(p,'pick','review');p.edge('review','override','Có');chain(p,'override','allow','flow')
p.small('wait',420,855,'Không: chờ thanh toán / bảo lãnh',kind='wait',w=390,h=52);p.edge('review','wait',sp='b',tp='t',color=C['amber'])
p.note('CÁC THAO TÁC KHÁC TRONG HỒ SƠ','Chuyển chủ cần xác nhận, đúng 1 chủ sở hữu (100%); có lưu trữ / khôi phục hồ sơ. Tệp hồ sơ: ảnh, PDF, DOCX.\nBảo lãnh không tự đổi sang PAID, không tự ghi doanh thu, không bỏ qua khám. Thu hồi bảo lãnh → kiểm tra lại điều kiện dịch vụ.')
# 04
p=Page(4,'Tiếp nhận ngựa','FLOW 04 · PHỐI HỢP TIẾP NHẬN NGỰA','Club Manager bảo lãnh và giám sát; Staff tiếp nhận, bác sĩ khám, Staff xếp chuồng, HLV nhận huấn luyện.')
n(p,0,'eligible','Đã trả phí còn hạn\nhoặc có bảo lãnh','HỆ THỐNG','shield')
n(p,1,'waiting','Đưa vào phòng chờ\nWAITING_FOR_VET','STABLE STAFF','building')
n(p,2,'claim','Một bác sĩ tiếp nhận\nCác bác sĩ khác xem','VETERINARIAN','medical')
n(p,3,'exam','Khám và kết luận\nGhi bác sĩ phụ trách','VETERINARIAN','doc')
n(p,4,'pending','Chờ xếp chuồng\nPENDING_STALL','HỆ THỐNG','clock')
chain(p,'eligible','waiting','claim','exam','pending')
p.section(540,'KHÁM XONG → ĐƯỢC CHĂM SÓC Ở CHUỒNG; HUẤN LUYỆN CÓ ĐIỀU KIỆN RIÊNG')
n(p,0,'monitor','Manager theo dõi\nmọi trạng thái',icon='screen',y=Z)
n(p,1,'stall','Chọn ô phù hợp\nXếp vào chuồng','STABLE STAFF','building',y=Z)
n(p,2,'fit','FIT, có ô hoạt động,\nkhông khóa tập,\nkhông chấn thương?',kind='decision',y=Z)
n(p,3,'train','Nhận ngựa\nvà lập lịch tập','HEAD TRAINER','horse',y=Z)
n(p,4,'ready','Huấn luyện theo\nphân công',role='HEAD TRAINER',kind='success',y=Z)
p.edge('pending','stall',sp='b',tp='t',via=[(1620,510),(540,510)])
p.edge('monitor','stall','Giám sát',dashed=True);p.edge('stall','fit');p.edge('fit','train','Có');p.edge('train','ready')
p.small('care',780,855,'Không: chăm sóc / điều trị, chưa tập',kind='wait',w=470,h=52);p.edge('fit','care',sp='b',tp='t',color=C['amber'])
p.note('ĐIỂM CHUYỂN GIAO BẮT BUỘC','Ngựa chấn thương vẫn được vào chuồng sau khám. Ngựa cách ly phải vào khu phù hợp, không trộn với ngựa bình thường.\nManager có API hỗ trợ tiếp nhận / phân chuồng; việc khám, kết luận sức khỏe và gỡ khóa tập thuộc bác sĩ. Bảo lãnh đi cùng quy trình này.')
# 05
p=Page(5,'Quản lý chuồng trại','FLOW 05 · CHUỒNG TRẠI & PHÂN BỔ','Quản lý khu chuồng, ô chuồng và thay đổi vị trí nuôi; giữ lịch sử phân bổ theo từng con ngựa.')
p.section(205,'A. TẠO / SỬA DANH MỤC CHUỒNG')
n(p,0,'cm','Mở danh sách\nkhu chuồng',icon='person')
n(p,1,'input','Nhập tên, vị trí\nvà các mã ô chuồng',icon='building')
n(p,2,'valid','Tên / mã không trùng?\nKhu có từ\n1 đến 4 ô?',kind='decision')
n(p,3,'save','Lưu danh mục\nchuồng / ô chuồng','HỆ THỐNG','building')
n(p,4,'done','Xem sơ đồ\nvà tình trạng trống',kind='success')
chain(p,'cm','input','valid');p.edge('valid','save','Có');p.edge('save','done');rejection(p,'valid',text='Không lưu\nĐiều chỉnh danh mục')
p.section(590,'B. XẾP / CHUYỂN Ô CHUỒNG')
n(p,0,'pick','Chọn ngựa đã khám\nvà ô chuồng đích',icon='horse',y=Z)
n(p,1,'eligible','Dịch vụ hợp lệ?\nÔ trống, khu mở,\nsức khỏe phù hợp?',kind='decision',y=Z)
n(p,2,'lock','Khóa chống trùng\nKết thúc phân bổ cũ','HỆ THỐNG','lock',y=Z)
n(p,3,'assign','Tạo phân bổ mới\nCập nhật STABLED','HỆ THỐNG','building',y=Z)
n(p,4,'result','Vị trí mới\nđược ghi nhận',kind='success',y=Z)
chain(p,'pick','eligible');p.edge('eligible','lock','Có');chain(p,'lock','assign','result')
p.small('blocked',420,855,'Không: chọn ô / khu khác phù hợp',w=400,h=52);p.edge('eligible','blocked',sp='b',tp='t',color=C['red'])
p.note('PHÂN BIỆT RÕ: NGỪNG HOẠT ĐỘNG / XÓA / GIẢI PHÓNG','Ngừng khu chuồng bị chặn khi còn ngựa. Giải phóng ô trả ngựa về PENDING_STALL và gỡ HLV phụ trách.\nCode xóa chuồng / ô hiện có thể giải phóng cả ngựa đang ở; cần rà soát tác động trước khi sử dụng. Không mô tả là chỉ xóa được ô trống.',tone='amber')
# 06
p=Page(6,'Duyệt và nhập vật tư','FLOW 06 · DUYỆT ĐỀ XUẤT & NHẬP KHO','Hai lựa chọn: duyệt trước rồi nhập sau, hoặc duyệt và xác nhận nhập ngay khi đã nhận hàng.')
n(p,0,'request','Mở đề xuất bổ sung\nPENDING_APPROVAL',icon='doc')
n(p,1,'review','Xem mặt hàng,\nsố lượng, lý do',icon='box')
n(p,2,'approve','Manager đồng ý\nbổ sung vật tư?',kind='decision')
n(p,3,'approved','Lưu người duyệt,\nghi chú · APPROVED','HỆ THỐNG','doc')
n(p,4,'now','Đã nhận hàng\nvà xác nhận\nnhập ngay?',kind='decision')
chain(p,'request','review','approve');p.edge('approve','approved','Đồng ý');p.edge('approved','now')
rejection(p,'approve',text='Từ chối: REJECTED\nKhông thay đổi tồn')
n(p,0,'later','Đã duyệt, chờ nhận\nChọn “Nhập kho”',icon='clock',y=Z)
n(p,1,'catalog','Chọn mặt hàng có sẵn\nhoặc tạo mặt hàng mới',icon='box',y=Z)
n(p,2,'receive','Đã duyệt, chưa nhập?\nDanh mục / đơn vị\nkhớp đề xuất?',kind='decision',y=Z)
n(p,3,'stock','Tăng tồn đúng 1 lần\nGhi nhận lần nhập','HỆ THỐNG','box',y=Z)
n(p,4,'done','FULFILLED\nHoàn tất đề xuất',kind='success',y=Z)
p.edge('now','later','Chưa',sp='b',tp='t',via=[(1620,610),(180,610)],labelpos=(400,575,100,30))
p.edge('now','catalog','Có',sp='l',tp='t',via=[(1440,340),(1440,575),(540,575)],labelpos=(1260,540,100,30))
chain(p,'later','catalog','receive');p.edge('receive','stock','Hợp lệ');p.edge('stock','done')
p.small('invalid',780,855,'Không hợp lệ: không tăng tồn',w=360,h=52);p.edge('receive','invalid',sp='b',tp='t',color=C['red'])
p.note('DUYỆT KHÔNG ĐỒNG NGHĨA VỚI ĐÃ NHẬP KHO','Yêu cầu đã bị từ chối / đã xử lý không được duyệt lại; đề xuất đã nhập không được cộng tồn lần nữa.\nBước duyệt và nhập kho không tạo khoản chi tài chính. Chi phí được ghi khi xuất dùng ở Flow 07.')
# 07
p=Page(7,'Xuất dùng và chi phí','FLOW 07 · XUẤT VẬT TƯ & GHI CHI PHÍ','Tồn kho dùng chung; số lượng xuất dùng và chi phí phải được ghi nhận cùng một giao dịch.')
n(p,0,'cm','Chọn vật tư\ntrong danh mục',icon='person')
n(p,1,'input','Nhập số lượng,\nđơn giá và nơi dùng',icon='doc')
n(p,2,'valid','Vật tư còn hoạt động?\nSố lượng / giá > 0?\nĐối tượng dùng đúng?',kind='decision')
n(p,3,'stock','Khóa dòng tồn kho\nCòn đủ số lượng?','HỆ THỐNG','lock',kind='decision')
n(p,4,'ready','Bắt đầu ghi nhận\nxuất dùng','HỆ THỐNG','box')
chain(p,'cm','input','valid');p.edge('valid','stock','Có');p.edge('stock','ready','Đủ')
rejection(p,'valid',text='Từ chối dữ liệu sai\nKhông thay đổi tồn')
rejection(p,'stock','insufficient','Không đủ tồn\nGiảm số lượng / bổ sung')
p.section(590,'CÙNG MỘT TRANSACTION: THÀNH CÔNG TOÀN BỘ HOẶC HOÀN TÁC')
n(p,0,'deduct','Trừ số lượng\ntrong tổng tồn','HỆ THỐNG','box',y=Z)
n(p,1,'usage','Lưu lịch sử\nxuất dùng vật tư','HỆ THỐNG','doc',y=Z)
n(p,2,'expense','Ghi chi phí VND\nSố lượng × đơn giá','HỆ THỐNG','chart',y=Z)
n(p,3,'result','Liên kết xuất dùng\nvới giao dịch tài chính','HỆ THỐNG','shield',y=Z)
n(p,4,'done','Manager xem tồn mới\nvà khoản chi',kind='success',y=Z)
p.edge('ready','deduct',sp='b',tp='t',via=[(1620,580),(180,580)])
chain(p,'deduct','usage','expense','result','done')
p.note('ĐỐI TƯỢNG SỬ DỤNG & NGUYÊN TẮC CHI PHÍ','Chọn đúng một ngựa hoặc một chuồng; vật tư y tế phải gắn với ngựa. Không xuất vượt số lượng đang có.\nChi phí này là ghi nhận chi phí sử dụng vật tư; không đồng nghĩa hệ thống tự trừ tiền từ tài khoản ngân hàng của admin.')
# 08
p=Page(8,'Quản lý giải đua','FLOW 08 · GIẢI ĐUA & ĐĂNG KÝ THI ĐẤU','Manager quản lý giải; HLV đề xuất ngựa dự đua; chủ ngựa quyết định việc tham gia.')
n(p,0,'cm','Tạo giải đua\nvà điều kiện dự đua',icon='trophy')
n(p,1,'validate','Kiểm tra thông tin\nngày đua, hạn đăng ký','HỆ THỐNG','shield')
n(p,2,'event','Giải đua sẵn sàng\nnhận đăng ký','HỆ THỐNG','trophy')
n(p,3,'trainer','Chọn ngựa phụ trách\nKiểm tra đủ điều kiện','HEAD TRAINER','horse')
n(p,4,'submit','Gửi chủ ngựa duyệt\nSUBMITTED','HEAD TRAINER','doc')
chain(p,'cm','validate','event','trainer','submit')
n(p,0,'owner','Chủ ngựa xem và\nquyết định đăng ký','HORSE OWNER','person',y=Z)
n(p,1,'approve','Chủ ngựa\nđồng ý?',role='HORSE OWNER',kind='decision',y=Z)
n(p,2,'confirm','Chuyển CONFIRMED\nĐược xác nhận ngay','HỆ THỐNG','check',y=Z)
n(p,3,'monitor','Theo dõi đăng ký\nvà giải đã diễn ra',icon='screen',y=Z)
n(p,4,'complete','Đánh dấu hoàn thành\nTừ ngày đua trở đi',icon='trophy',kind='success',y=Z)
p.edge('submit','owner',sp='b',tp='t',via=[(1620,540),(180,540)])
p.edge('owner','approve');p.edge('approve','confirm','Có');chain(p,'confirm','monitor','complete')
p.small('reject',420,855,'Không: REJECT + lý do của chủ ngựa',w=420,h=52);p.edge('approve','reject',sp='b',tp='t',color=C['red'])
p.note('GIỚI HẠN VAI TRÒ & HIỆN TRẠNG','Manager không duyệt thay chủ ngựa. Ngựa không đủ điều kiện / quá hạn / đăng ký trùng bị chặn trước khi gửi.\nAPI còn hỗ trợ sửa, ngừng giải và xác nhận dữ liệu APPROVE cũ; UI hiện chủ yếu tạo giải, xem đăng ký và hoàn thành giải.')
# 09
p=Page(9,'Tài chính và báo cáo','FLOW 09 · TÀI CHÍNH & BÁO CÁO','Ghi sổ thu / chi bằng VND; tra cứu chứng từ, so sánh các tháng và xem báo cáo chủ ngựa.')
p.section(205,'A. GHI NHẬN THU / CHI')
n(p,0,'cm','Mở Tài chính\nvà chọn thêm giao dịch',icon='person')
n(p,1,'input','Nhập loại, danh mục,\nsố tiền, đối tượng, ngày',icon='doc')
n(p,2,'valid','Dữ liệu hợp lệ?\nĐối tượng và nguồn\nđược phép ghi?',kind='decision')
n(p,3,'record','Thêm vào sổ cái\nLiên kết dữ liệu nguồn','HỆ THỐNG','chart')
n(p,4,'done','Tra cứu giao dịch\nvừa ghi',kind='success')
chain(p,'cm','input','valid');p.edge('valid','record','Có');p.edge('record','done');rejection(p,'valid',text='Không ghi sổ\nBáo nguyên nhân')
p.section(590,'B. THEO DÕI VÀ XUẤT BÁO CÁO')
n(p,0,'filter','Chọn tháng / kỳ\nvà điều kiện lọc',icon='clock',y=Z)
n(p,1,'aggregate','Tổng hợp thu, chi\nvà chênh lệch VND','HỆ THỐNG','chart',y=Z)
n(p,2,'compare','So sánh tháng chọn\nvới tháng đối chiếu',icon='screen',y=Z)
n(p,3,'details','Mở chi tiết giao dịch\nhoặc báo cáo chủ ngựa',icon='doc',y=Z)
n(p,4,'export','Xuất CSV / tải JSON\ntheo loại báo cáo',kind='success',y=Z)
chain(p,'filter','aggregate','compare','details','export')
p.note('TÀI CHÍNH KHÔNG BỊ TÍNH HAI LẦN','Không ghi thủ công lại khoản đã được sinh tự động, ví dụ xuất vật tư. Luồng tài chính chỉ thêm giao dịch, không sửa / xóa sổ cái.\nBáo cáo chủ ngựa có job 06:00 mỗi ngày và thông báo mới. PDF chưa được coi là bước hoàn thiện trong sơ đồ này.')
# 10
p=Page(10,'Nhật ký hệ thống','FLOW 10 · TRA CỨU AUDIT LOG','Theo dõi ai đã làm gì, trên dữ liệu nào và lúc nào; dùng để đối chiếu thao tác và xử lý sai lệch.')
n(p,0,'actor','Người dùng thực hiện\nthao tác trong hệ thống','CÁC VAI TRÒ','person')
n(p,1,'action','Đăng nhập hoặc\nthay đổi dữ liệu','HỆ THỐNG','doc')
n(p,2,'audit','Ghi nhật ký thao tác\nkhi xử lý thành công','HỆ THỐNG','shield')
n(p,3,'store','Lưu người thao tác,\nthời điểm và đối tượng','HỆ THỐNG','doc')
n(p,4,'immutable','Nhật ký chỉ thêm\nKhông sửa / xóa','HỆ THỐNG','lock',kind='success')
chain(p,'actor','action','audit','store','immutable')
p.section(590,'CLUB MANAGER TRA CỨU VÀ ĐỐI SOÁT')
n(p,0,'cm','Mở Nhật ký\nhệ thống',icon='person',y=Z)
n(p,1,'filter','Lọc người thao tác,\nphân hệ, loại, thời gian',icon='doc',y=Z)
n(p,2,'search','Tìm và phân trang\ncác bản ghi phù hợp','HỆ THỐNG','screen',y=Z)
n(p,3,'detail','Mở chi tiết thao tác\nXem trước / sau nếu có',icon='doc',y=Z)
n(p,4,'review','Đối chiếu dữ liệu gốc\nQuay về phân hệ xử lý',kind='success',y=Z)
chain(p,'cm','filter','search','detail','review')
p.note('PHẠM VI TRA CỨU','Không có kết quả lọc là trạng thái rỗng, không phải lỗi. Dữ liệu trước / sau chỉ hiển thị khi bản ghi thực tế có lưu.\nManager không chỉnh sửa hoặc xóa audit. Không biến audit thành bước phê duyệt cho mọi thao tác của các vai trò khác.')
# XML and same-model SVGs
mx=E.Element('mxfile',{'host':'app.diagrams.net','modified':'2026-11-06T00:00:00.000Z','agent':'Codex','version':'24.7.17','type':'device','compressed':'false'})
for p in pages:mx.append(p.diagram)
E.indent(mx,space='  ')
dest=OUT/'CLUB_MANAGER_WORKFLOW_REVIEWED.drawio'
E.ElementTree(mx).write(dest,encoding='utf-8',xml_declaration=True)
root_dest = Path(r'D:\HORSE-PROJECT\CLUB_MANAGER_WORKFLOW.drawio')
docs_dest = OUT/'CLUB_MANAGER_WORKFLOW.drawio'
E.ElementTree(mx).write(root_dest,encoding='utf-8',xml_declaration=True)
E.ElementTree(mx).write(docs_dest,encoding='utf-8',xml_declaration=True)
for p in pages:(TMP/f'flow-{p.num:02}.svg').write_text(p.render(),encoding='utf-8')
# A standalone overview preview is portable and has no external images or fonts.
(OUT/'CLUB_MANAGER_WORKFLOW_PREVIEW.svg').write_text(pages[0].render(),encoding='utf-8')
# Validate structure, references, grouped geometry, isolated nodes, overlaps, and page extent.
issues=[];stats=[]
for p in pages:
 ids=[c.attrib['id'] for c in p.root]
 if len(ids)!=len(set(ids)):issues.append(f'Page {p.num}: duplicate ids')
 for c in p.root:
  if c.attrib.get('parent') not in [None,*ids]:issues.append(f'Invalid parent {c.attrib}')
  for a in ['source','target']:
   if a in c.attrib and c.attrib[a] not in ids:issues.append(f'Invalid edge {c.attrib}')
 for id,(x,y,w,h) in p.nodes.items():
  if not (0<=x<x+w<=W and 0<=y<y+h<=H):issues.append(f'Out of page: {p.num}/{id}')
  if not any(id in e[:2] for e in p.edges):issues.append(f'Isolated workflow node: {p.num}/{id}')
 for i,(a,ar) in enumerate(p.nodes.items()):
  for b,br in list(p.nodes.items())[i+1:]:
   ax,ay,aw,ah=ar;bx,by,bw,bh=br
   if ax<bx+bw and ax+aw>bx and ay<by+bh and ay+ah>by:issues.append(f'Overlap: {p.num}/{a}/{b}')
 stats.append({'page':p.num,'name':p.name,'nodes':len(p.nodes),'edges':len(p.edges),'cells':len(ids)})
E.parse(dest)
(TMP/'validation.json').write_text(json.dumps({'issues':issues,'pages':stats},ensure_ascii=False,indent=2),encoding='utf-8')
if issues:raise SystemExit('\n'.join(issues))
print(json.dumps({'file':str(dest),'pages':len(pages),'nodes':sum(len(p.nodes) for p in pages),'edges':sum(len(p.edges) for p in pages),'size':dest.stat().st_size,'validation':'OK'},ensure_ascii=False))

