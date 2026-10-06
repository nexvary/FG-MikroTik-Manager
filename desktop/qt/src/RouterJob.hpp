#pragma once
#include "RouterClient.hpp"
#include <coroutine>
#include <exception>
#include <memory>
#include <stdexcept>
#include <utility>
// Jobs own their coroutine frame; a weak cancellation token prevents late network
// replies from touching a frame destroyed when the workspace closes.
class RouterJob {
public:
 struct promise_type {
  std::exception_ptr failure;
  std::coroutine_handle<> continuation=std::noop_coroutine();
  RouterJob get_return_object(){return RouterJob(std::coroutine_handle<promise_type>::from_promise(*this));}
  std::suspend_always initial_suspend()noexcept{return {};}
  struct Final {bool await_ready()noexcept{return false;}std::coroutine_handle<> await_suspend(std::coroutine_handle<promise_type> h)noexcept{return h.promise().continuation;}void await_resume()noexcept{}};
  Final final_suspend()noexcept{return {};}
  void return_void()noexcept{}
  void unhandled_exception()noexcept{failure=std::current_exception();}
 };
 using Handle=std::coroutine_handle<promise_type>;
 explicit RouterJob(Handle h={}):handle(h){}
 RouterJob(RouterJob&&o)noexcept:handle(std::exchange(o.handle,{})){}
 RouterJob&operator=(RouterJob&&o)noexcept{if(this!=&o){if(handle)handle.destroy();handle=std::exchange(o.handle,{});}return *this;}
 RouterJob(const RouterJob&)=delete;~RouterJob(){if(handle)handle.destroy();}
 void start(){handle.resume();}
 struct Awaiter {Handle handle;bool await_ready()const noexcept{return !handle||handle.done();}std::coroutine_handle<> await_suspend(std::coroutine_handle<> next)noexcept{handle.promise().continuation=next;return handle;}void await_resume(){if(handle.promise().failure)std::rethrow_exception(handle.promise().failure);}};
 Awaiter operator co_await()const noexcept{return {handle};}
private: Handle handle;
};
struct RouterAwait {
 RouterClient *client;std::weak_ptr<int> lifetime;QString menu,action;QJsonObject attributes;RouterReply reply;bool requireSuccess=true;
 bool await_ready()const noexcept{return false;}
 void await_suspend(std::coroutine_handle<> continuation){auto weak=lifetime;client->execute(menu,action,attributes,[this,weak,continuation](RouterReply r){if(weak.expired())return;reply=std::move(r);continuation.resume();});}
 RouterReply await_resume(){if(requireSuccess&&!reply.ok())throw std::runtime_error((reply.error+(reply.uncertain?"; VERIFY_OUTCOME_BEFORE_RETRY":"")).toStdString());return std::move(reply);}
};
