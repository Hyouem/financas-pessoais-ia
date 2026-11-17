import { fileURLToPath } from 'node:url';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { chromium } from '@playwright/test';
const base=process.env.PORTFOLIO_BASE_URL||'http://localhost:5175';
const email=`demo-${Date.now()}@example.test`;
const password='DemoPassword!2026';
const call=async(path,method='GET',data,token)=>{
 const r=await fetch(base+'/api'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:data?JSON.stringify(data):undefined});
 const result=await r.json();if(!r.ok)throw new Error(`${path}: ${JSON.stringify(result)}`);return result;
};
const {token}=await call('/auth/register','POST',{email,password});
const acc=await call('/accounts','POST',{name:'Conta Principal',initialBalance:4200},token);
const inc=await call('/categories','POST',{name:'Salário',kind:'INCOME'},token);
const housing=await call('/categories','POST',{name:'Moradia',kind:'EXPENSE'},token);
const food=await call('/categories','POST',{name:'Alimentação',kind:'EXPENSE'},token);
const transport=await call('/categories','POST',{name:'Transporte',kind:'EXPENSE'},token);
const leisure=await call('/categories','POST',{name:'Lazer',kind:'EXPENSE'},token);
const mm=new Date();const month=mm.toISOString().slice(0,7);
const day=(n)=>`${month}-${String(n).padStart(2,'0')}`;
const todayNum=Number(mm.toLocaleDateString('en-CA').slice(-2));
const last=Math.min(todayNum,20);
await call('/transactions','POST',{accountId:acc.id,categoryId:inc.id,kind:'INCOME',amount:'4800.00',note:'Salário mensal',occurredOn:day(Math.min(5,last))},token);
for(let i=1;i<=12;i++){
 const c=[housing,food,transport,leisure][i%4];
 await call('/transactions','POST',{accountId:acc.id,categoryId:c.id,kind:'EXPENSE',amount:String(60+(i%5)*52)+'.00',note:['Aluguel e contas','Mercado e compras','Transporte','Cinema e lazer'][i%4],occurredOn:day(1+(i*2)%(last||1))},token);
}
await call('/budgets','POST',{categoryId:food.id,month,limitAmount:'1250.00'},token);
await call('/budgets','POST',{categoryId:transport.id,month,limitAmount:'600.00'},token);
await call('/recurrences','POST',{accountId:acc.id,categoryId:food.id,kind:'EXPENSE',amount:'150.00',note:'Compra recorrente',dayOfMonth:10},token);
await call('/recurrences/apply?month='+month,'POST',undefined,token);
await call('/recurrences/apply?month='+month,'POST',undefined,token);
let browser;
for(const channel of ['chrome','msedge']){try{browser=await chromium.launch({channel,headless:true});break;}catch{}}
if(!browser)throw new Error('Chrome ou Edge nao encontrado para captura Playwright');
try{const page=await browser.newPage({viewport:{width:1536,height:1050},deviceScaleFactor:1});await page.goto(base,{waitUntil:'domcontentloaded'});
 await page.evaluate(t=>localStorage.setItem('token',t),token);await page.reload({waitUntil:'networkidle'});
 await page.locator('text=Conta Principal').first().waitFor({timeout:20000});
 const out=fileURLToPath(new URL('../../docs/images/funcionamento.png',import.meta.url));
 mkdirSync(dirname(out),{recursive:true});
 await page.screenshot({path:out,fullPage:true,animations:'disabled'});
 console.log('Captura real da plataforma financeira concluida.');
}finally{await browser.close();}
